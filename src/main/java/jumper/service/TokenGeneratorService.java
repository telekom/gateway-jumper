// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.WeakKeyException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.KeyInfo;
import jumper.util.OauthTokenUtil;
import jumper.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenGeneratorService {

  private final KeyInfoService keyInfoService;

  private String fromRealm(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt) {
    KeyInfo keyInfo;
    try {
      log.debug("GatewayToken or OneToken: Loading keyInfo");
      keyInfo = keyInfoService.getKeyInfo();

    } catch (IOException e1) {
      log.error("IOException", e1);
      throw new RuntimeException("Error while generating LMS token", e1);
    } catch (GeneralSecurityException e2) {
      log.error("GeneralSecurityException", e2);
      throw new RuntimeException("Could not create PrivateKey from key file", e2);
    }

    return generateToken(claims, issuer, expiration, issuedAt, keyInfo);
  }

  private String fromKey(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, String key) {

    KeyInfo keyInfo = new KeyInfo();
    try {
      keyInfo.setPk(RsaUtils.getPrivateKey(key));
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Invalid key configuration: " + e.getMessage());
    }

    return generateToken(claims, issuer, expiration, issuedAt, keyInfo);
  }

  public String createJwtTokenFromKey(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, String key) {
    return fromKey(claims, issuer, expiration, issuedAt, key);
  }

  private String generateToken(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, KeyInfo key) {
    try {
      return Jwts.builder()
          .setClaims(claims)
          .setIssuer(issuer)
          .setExpiration(expiration)
          .setIssuedAt(issuedAt)
          .signWith(key.getPk(), SignatureAlgorithm.RS256)
          .setHeaderParam("kid", key.getKid())
          .setHeaderParam("typ", "JWT")
          .compact();
    } catch (WeakKeyException e) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Key is too weak: The JWT JWA Specification (RFC 7518, Section 3.3) states that keys used"
              + " with RS256 MUST have a size >= 2048 bits.");
    }
  }

  /**
   * Core LMS token generation method. Builds a signed JWT from the consumer token claims and
   * request context. The {@code azp} value distinguishes the token's purpose:
   *
   * <ul>
   *   <li>{@code "stargate"} — Provider-facing OneToken (Last Mile Security towards the upstream)
   *   <li>{@code "gateway"} — Mesh LMS token (cross-zone gateway-to-gateway authentication)
   * </ul>
   */
  private String generateLmsToken(
      JumperConfig jc,
      String azp,
      String operation,
      String issuer,
      String publisherId,
      String subscriberId,
      boolean legacy) {

    String consumerTokenWithoutSignature =
        OauthTokenUtil.getTokenWithoutSignature(jc.getConsumerToken());

    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(consumerTokenWithoutSignature);

    Date issuedAt = consumerTokenClaims.getBody().getIssuedAt();
    Date expiration = consumerTokenClaims.getBody().getExpiration();
    String sub = consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_SUB, String.class);
    String aud = consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_AUD, String.class);

    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
    claims.put(Constants.TOKEN_CLAIM_AZP, azp);
    claims.put(Constants.TOKEN_CLAIM_SUB, sub);
    // requestPath is only set on provider LMS tokens, not on mesh LMS tokens, because
    // RequestFilter.calculateFinalApiUri() is only called on the provider path.
    if (Objects.nonNull(jc.getRequestPath())) {
      claims.put(Constants.TOKEN_CLAIM_REQUEST_PATH, jc.getRequestPath());
    }
    claims.put(Constants.TOKEN_CLAIM_OPERATION, operation);
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, jc.getConsumer());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_ZONE, jc.getConsumerOriginZone());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, jc.getConsumerOriginStargate());

    if (legacy) {
      String consumerTokenSignature = OauthTokenUtil.getSignature(jc.getConsumerToken());
      claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SIGNATURE, consumerTokenSignature);

    } else {
      // env is only set on provider LMS tokens, not on mesh LMS tokens, because
      // consumer-side proxy routes do not inject the `environment` header.
      if (Objects.nonNull(jc.getEnvName())) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT, jc.getEnvName());
      }

      if (Objects.nonNull(jc.getSecurityScopes())) {
        claims.put(Constants.TOKEN_CLAIM_SCOPE, jc.getSecurityScopes());
      }

      if (Objects.nonNull(publisherId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID, publisherId);
      }

      if (Objects.nonNull(subscriberId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID, subscriberId);
        claims.put(Constants.TOKEN_CLAIM_AUD, subscriberId);
      }
    }

    if (StringUtils.isNotBlank(aud)) {
      claims.put(Constants.TOKEN_CLAIM_AUD, aud);
    }

    return fromRealm(claims, issuer, expiration, issuedAt);
  }

  /**
   * Generates a provider-facing LMS token (OneToken) with {@code azp: "stargate"}.
   *
   * <p>Used on real routes to replace the consumer's Iris token before forwarding to the upstream
   * API.
   */
  public String generateProviderLmsToken(
      JumperConfig jc,
      String operation,
      String issuer,
      String publisherId,
      String subscriberId,
      boolean legacy) {
    return generateLmsToken(jc, "stargate", operation, issuer, publisherId, subscriberId, legacy);
  }

  /**
   * Generates a mesh LMS token with {@code azp: "gateway"}.
   *
   * <p>Used on proxy routes for cross-zone gateway-to-gateway authentication. Replaces the
   * Iris-issued gateway client_credentials token so that no shared {@code gateway} secret is
   * required. The provider zone validates this token against the consumer zone's StarGate JWKS.
   */
  public String generateMeshLmsToken(JumperConfig jc, String operation, String issuer) {
    return generateLmsToken(jc, "gateway", operation, issuer, null, null, false);
  }

  public String generateGatewayTokenForPublisher(String issuer, String realm) {
    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
    claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, "gateway");

    return fromRealm(
        claims,
        issuer,
        new Date(System.currentTimeMillis() + 300 * 1000),
        new Date(System.currentTimeMillis()));
  }
}

// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.*;
import java.security.Key;
import java.security.interfaces.RSAKey;
import java.util.*;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.KeyInfo;
import jumper.util.OauthTokenUtil;
import jumper.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenGeneratorService {

  private final KeyInfoService keyInfoService;

  private String fromRealm(Claims claims, String issuer, Date expiration, Date issuedAt) {
    log.debug("GatewayToken or OneToken: Loading keyInfo");
    KeyInfo keyInfo = keyInfoService.getKeyInfo();
    return generateToken(claims, issuer, expiration, issuedAt, keyInfo);
  }

  private String fromKey(Claims claims, String issuer, Date expiration, Date issuedAt, String key) {

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
      Claims claims, String issuer, Date expiration, Date issuedAt, String key) {
    return fromKey(claims, issuer, expiration, issuedAt, key);
  }

  private String generateToken(
      Claims claims, String issuer, Date expiration, Date issuedAt, KeyInfo key) {
    // Enforce RFC 7518 (Section 3.3) up front: RS256 requires RSA keys >= 2048 bits. jjwt surfaces
    // a too-weak key as a SignatureException at signing time; checking here keeps the 401 mapping
    // explicit and independent of jjwt's internal exception type.
    assertKeyStrongEnoughForRs256(key.getPk());

    JwtBuilder builder =
        Jwts.builder()
            .claims(claims)
            .issuer(issuer)
            .expiration(expiration)
            .issuedAt(issuedAt)
            .signWith(key.getPk(), Jwts.SIG.RS256);

    // Preserve the historical header shape: {"typ":"JWT","alg":"RS256"} (+ "kid" when present).
    // A null kid (e.g. the external-IDP client assertion signed via fromKey) must be omitted.
    var header = builder.header().add("typ", "JWT");
    if (Objects.nonNull(key.getKid())) {
      header.keyId(key.getKid());
    }

    return header.and().compact();
  }

  private static void assertKeyStrongEnoughForRs256(Key key) {
    if (key instanceof RSAKey rsaKey && rsaKey.getModulus().bitLength() < 2048) {
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
      String subscriberId) {

    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(jc.getConsumerToken());

    Date issuedAt = consumerTokenClaims.getPayload().getIssuedAt();
    Date expiration = consumerTokenClaims.getPayload().getExpiration();
    String sub = consumerTokenClaims.getPayload().get(Constants.TOKEN_CLAIM_SUB, String.class);
    Set<String> consumerAudiences = consumerTokenClaims.getPayload().getAudience();

    ClaimsBuilder claims =
        Jwts.claims()
            .add(Constants.TOKEN_CLAIM_TYP, "Bearer")
            .add(Constants.TOKEN_CLAIM_AZP, azp)
            .subject(sub)
            .add(Constants.TOKEN_CLAIM_OPERATION, operation)
            .add(Constants.TOKEN_CLAIM_CLIENT_ID, jc.getConsumer())
            .add(Constants.TOKEN_CLAIM_ORIGIN_ZONE, jc.getConsumerOriginZone())
            .add(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, jc.getConsumerOriginStargate());

    // requestPath is not strictly required for mesh LMS validation.
    if (Objects.nonNull(jc.getRequestPath())) {
      claims.add(Constants.TOKEN_CLAIM_REQUEST_PATH, jc.getRequestPath());
    }

    // env is only set on provider LMS tokens, not on mesh LMS tokens, because
    // consumer-side proxy routes do not inject the `environment` header.
    if (Objects.nonNull(jc.getEnvName())) {
      claims.add(Constants.TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT, jc.getEnvName());
    }

    if (Objects.nonNull(jc.getSecurityScopes())) {
      claims.add(Constants.TOKEN_CLAIM_SCOPE, jc.getSecurityScopes());
    }

    if (Objects.nonNull(publisherId)) {
      claims.add(Constants.TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID, publisherId);
    }

    if (Objects.nonNull(subscriberId)) {
      claims.add(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID, subscriberId);
    }

    // A lone audience uses .single(...) rather than .add(...): jjwt only collapses the aud claim
    // to a plain JSON string (matching pre-migration wire format) via .single(...); .add(...)
    // always emits a JSON array, even when adding just one element.
    if (Objects.nonNull(consumerAudiences) && !consumerAudiences.isEmpty()) {
      if (consumerAudiences.size() == 1) {
        claims.audience().single(consumerAudiences.iterator().next());
      } else {
        claims.audience().add(consumerAudiences).and();
      }
    } else if (Objects.nonNull(subscriberId)) {
      claims.audience().single(subscriberId);
    }

    return fromRealm(claims.build(), issuer, expiration, issuedAt);
  }

  /**
   * Generates a provider-facing LMS token with {@code azp: "stargate"}.
   *
   * <p>Used on real routes to replace the consumer's Iris token before forwarding to the upstream
   * API.
   */
  public String generateProviderLmsToken(
      JumperConfig jc, String operation, String issuer, String publisherId, String subscriberId) {
    return generateLmsToken(jc, "stargate", operation, issuer, publisherId, subscriberId);
  }

  /**
   * Generates a mesh LMS token with {@code azp: "gateway"}.
   *
   * <p>Used on proxy routes for cross-zone gateway-to-gateway authentication. The provider zone
   * validates this token against the consumer zone's StarGate JWKS.
   */
  public String generateMeshLmsToken(JumperConfig jc, String operation, String issuer) {
    return generateLmsToken(jc, "gateway", operation, issuer, null, null);
  }

  public String generateGatewayTokenForPublisher(String issuer, String realm) {
    Claims claims =
        Jwts.claims()
            .add(Constants.TOKEN_CLAIM_TYP, "Bearer")
            .add(Constants.TOKEN_CLAIM_AZP, "stargate")
            .add(Constants.TOKEN_CLAIM_CLIENT_ID, "gateway")
            .build();

    return fromRealm(
        claims,
        issuer,
        new Date(System.currentTimeMillis() + 300 * 1000),
        new Date(System.currentTimeMillis()));
  }
}

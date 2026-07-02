// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.WeakKeyException;
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

  private String fromRealm(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt) {
    return fromRealm(claims, issuer, expiration, issuedAt, Set.of());
  }

  private String fromRealm(
      HashMap<String, String> claims,
      String issuer,
      Date expiration,
      Date issuedAt,
      Set<String> audiences) {
    log.debug("GatewayToken or OneToken: Loading keyInfo");
    KeyInfo keyInfo = keyInfoService.getKeyInfo();
    return generateToken(claims, issuer, expiration, issuedAt, keyInfo, audiences);
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

    return generateToken(claims, issuer, expiration, issuedAt, keyInfo, Set.of());
  }

  public String createJwtTokenFromKey(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, String key) {
    return fromKey(claims, issuer, expiration, issuedAt, key);
  }

  private String generateToken(
      HashMap<String, String> claims,
      String issuer,
      Date expiration,
      Date issuedAt,
      KeyInfo key,
      Set<String> audiences) {
    try {
      JwtBuilder builder =
          Jwts.builder()
              .claims(claims)
              .issuer(issuer)
              .expiration(expiration)
              .issuedAt(issuedAt)
              .header()
              .keyId(key.getKid())
              .add("typ", "JWT")
              .and();

      if (!audiences.isEmpty()) {
        builder = builder.audience().add(audiences).and();
      }

      return builder.signWith(key.getPk(), Jwts.SIG.RS256).compact();
    } catch (WeakKeyException e) {
      throw weakKeyResponse();
    } catch (io.jsonwebtoken.security.SignatureException e) {
      // jjwt's signWith(Key, SecureDigestAlgorithm) - the non-deprecated replacement for
      // signWith(Key, SignatureAlgorithm) - validates key strength lazily inside compact() and
      // wraps the resulting WeakKeyException in a SignatureException, unlike the deprecated
      // overload which threw WeakKeyException directly.
      if (e.getCause() instanceof WeakKeyException) {
        throw weakKeyResponse();
      }
      throw e;
    }
  }

  private static ResponseStatusException weakKeyResponse() {
    return new ResponseStatusException(
        HttpStatus.UNAUTHORIZED,
        "Key is too weak: The JWT JWA Specification (RFC 7518, Section 3.3) states that keys used"
            + " with RS256 MUST have a size >= 2048 bits.");
  }

  public String generateEnhancedLastMileGatewayToken(
      JumperConfig jc,
      String operation,
      String issuer,
      String publisherId,
      String subscriberId,
      boolean legacy) {

    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(jc.getConsumerToken());

    Date issuedAt = consumerTokenClaims.getBody().getIssuedAt();
    Date expiration = consumerTokenClaims.getBody().getExpiration();
    String sub = consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_SUB, String.class);
    // the incoming consumer token's own audience(s) - possibly more than one, per RFC 7519 - take
    // precedence below over the subscriber-derived one.
    Set<String> consumerAudiences = OauthTokenUtil.getAudiences(consumerTokenClaims.getBody());

    HashMap<String, String> claims = new HashMap<>();
    claims.put(Constants.TOKEN_CLAIM_TYP, "Bearer");
    claims.put(Constants.TOKEN_CLAIM_AZP, "stargate");
    claims.put(Constants.TOKEN_CLAIM_SUB, sub);
    claims.put(Constants.TOKEN_CLAIM_REQUEST_PATH, jc.getRequestPath());
    claims.put(Constants.TOKEN_CLAIM_OPERATION, operation);
    claims.put(Constants.TOKEN_CLAIM_CLIENT_ID, jc.getConsumer());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_ZONE, jc.getConsumerOriginZone());
    claims.put(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, jc.getConsumerOriginStargate());

    if (legacy) {
      String consumerTokenSignature = OauthTokenUtil.getSignature(jc.getConsumerToken());
      claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SIGNATURE, consumerTokenSignature);

    } else {
      claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_ENVIRONMENT, jc.getEnvName());

      if (Objects.nonNull(jc.getSecurityScopes())) {
        claims.put(Constants.TOKEN_CLAIM_SCOPE, jc.getSecurityScopes());
      }

      if (Objects.nonNull(publisherId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_PUBLISHER_ID, publisherId);
      }

      if (Objects.nonNull(subscriberId)) {
        claims.put(Constants.TOKEN_CLAIM_ACCESS_TOKEN_SUBSCRIBER_ID, subscriberId);
      }
    }

    Set<String> audiences =
        Objects.nonNull(subscriberId) && !legacy ? Set.of(subscriberId) : Set.of();
    if (!consumerAudiences.isEmpty()) {
      audiences = consumerAudiences;
    }

    return fromRealm(claims, issuer, expiration, issuedAt, audiences);
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

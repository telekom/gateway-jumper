// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.WeakKeyException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import jumper.model.config.KeyInfo;
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

  public String fromRealm(
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

  public String fromKey(
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
          "Key is too weak: The JWT JWA Specification (RFC 7518, Section 3.3) states that keys used with RS256 MUST have a size >= 2048 bits.");
    }
  }
}

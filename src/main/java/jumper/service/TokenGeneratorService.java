// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.WeakKeyException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import jumper.model.config.KeyInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class TokenGeneratorService {

  private static String securityPath;
  private static String securityFile;

  @Value("${jumper.security.dir:keypair}")
  private void setSecurityPath(String name) {
    securityPath = name;
  }

  @Value("${jumper.security.file:private.json}")
  private void setSecurityFile(String name) {
    securityFile = name;
  }

  public String fromRealm(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, String realm) {
    Map<String, KeyInfo> keyInfoMap;

    try {
      log.debug("GatewayToken or OneToken: Loading keyInfo");
      keyInfoMap = loadKeyInfo();

    } catch (IOException e1) {
      log.error("IOException", e1);
      throw new RuntimeException("Error while generating LMS token, key info missing");
    }

    if (!keyInfoMap.containsKey(realm)) {
      throw new RuntimeException("key info missing for realm " + realm);
    }

    return generateToken(claims, issuer, expiration, issuedAt, keyInfoMap.get(realm));
  }

  public String fromKey(
      HashMap<String, String> claims, String issuer, Date expiration, Date issuedAt, String key) {

    KeyInfo keyInfo = new KeyInfo();
    try {
      keyInfo.setPk(key);
    } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
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

  public static Map<String, KeyInfo> loadKeyInfo() throws IOException {
    Path kidFile =
        Path.of(
            System.getProperty("user.dir")
                + File.separator
                + securityPath
                + File.separator
                + securityFile);

    TypeReference<HashMap<String, KeyInfo>> typeRef = new TypeReference<>() {};

    return new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .readValue(Files.readString(kidFile), typeRef);
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.util.Base64;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class OauthTokenUtil {

  private static final JwtParser jwtParser =
      Jwts.parser().unsecured().clockSkewSeconds(3600).build();

  // Private constructor to prevent instantiation
  private OauthTokenUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static String getTokenWithoutSignature(String consumerToken) {

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String[] token = consumerToken.split(" ");
    String[] splitToken = token[1].split("\\.");

    return splitToken[0] + "." + splitToken[1] + ".";
  }

  public static String getSignature(String consumerToken) {

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String[] token = consumerToken.split(" ");
    String[] splitToken = token[1].split("\\.");

    return splitToken[2];
  }

  public static String getClaimFromToken(String consumerToken, String claimName) {
    String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);
    return getAllClaimsFromToken(consumerTokenWithoutSignature)
        .getBody()
        .get(claimName, String.class);
  }

  public static Jwt<?, Claims> getAllClaimsFromToken(String consumerToken) {

    try {
      // For tokens with signature removed (header.payload.), we need to modify the header
      // to indicate it's unsecured (alg: none) before parsing
      String[] parts = consumerToken.split("\\.");
      if (parts.length >= 2) {
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
        String modifiedHeader =
            headerJson.replaceAll("\"alg\"\\s*:\\s*\"[^\"]+\"", "\"alg\":\"none\"");
        String modifiedHeaderEncoded =
            Base64.getUrlEncoder().withoutPadding().encodeToString(modifiedHeader.getBytes());
        String unsecuredToken = modifiedHeaderEncoded + "." + parts[1] + ".";
        return jwtParser.parseUnsecuredClaims(unsecuredToken);
      }
      throw new IllegalArgumentException("Invalid JWT format");
    } catch (SignatureException e) {
      log.error("SignatureException: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    } catch (ExpiredJwtException e) {
      log.error("ExpiredJwtException: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    } catch (UnsupportedJwtException e) {
      log.error("UnsupportedJwtException: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    } catch (MalformedJwtException e) {
      log.error("MalformedJwtException: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    } catch (IllegalArgumentException e) {
      log.error("IllegalArgumentException: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("Unexpected exception parsing JWT: {}", e.getMessage(), e);
      throw new IllegalStateException("Was not able to parse consumer token: " + e.getMessage(), e);
    }
  }
}

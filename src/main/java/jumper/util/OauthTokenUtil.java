// SPDX-FileCopyrightText: 2023-2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public final class OauthTokenUtil {

  private static final JwtParser jwtParser =
      Jwts.parser().unsecured().clockSkewSeconds(3600).build();

  // jjwt >= 0.12 only parses unsecured JWTs whose header declares "alg":"none".
  // The input token is a signed JWT whose claims we read without verifying the
  // signature, so we strip the signature and swap in an unsecured header for parsing.
  private static final String UNSECURED_HEADER =
      Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

  // Private constructor to prevent instantiation
  private OauthTokenUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  private static String getTokenWithoutSignature(String token) {
    String fullyProcessedToken = processToken(token);

    int firstDot = fullyProcessedToken.indexOf(".");
    int secondDot = fullyProcessedToken.indexOf(".", firstDot + 1);

    if (secondDot == -1) {
      throw new IllegalArgumentException("Invalid token format");
    }

    return fullyProcessedToken.substring(0, secondDot + 1);
  }

  public static Jwt<?, Claims> getAllClaimsFromToken(String token) {
    String tokenWithoutSignature = getTokenWithoutSignature(token);

    int firstDot = tokenWithoutSignature.indexOf('.');
    String unsecuredToken = UNSECURED_HEADER + tokenWithoutSignature.substring(firstDot);

    try {
      return jwtParser.parseUnsecuredClaims(unsecuredToken);
    } catch (Exception e) {
      log.error("Failed to parse token", e);
      throw e;
    }
  }

  private static @NonNull String processToken(String token) {
    if (Objects.isNull(token)) {
      throw new IllegalArgumentException("Token not provided, but expected");
    }

    String trimmedToken = token.trim();

    final String BEARER_PREFIX = "Bearer ";
    if (!trimmedToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw new IllegalArgumentException("Invalid token format, Missing Bearer prefix");
    }

    String tokenWithoutBearer = trimmedToken.substring(BEARER_PREFIX.length());
    return tokenWithoutBearer.trim();
  }
}

// SPDX-FileCopyrightText: 2023-2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.*;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public final class OauthTokenUtil {

  private static final JwtParser jwtParser =
      Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build();

  // Private constructor to prevent instantiation
  private OauthTokenUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static String getSignature(String consumerToken) {
    return parseTokenParts(consumerToken).signature();
  }

  static String getTokenWithoutSignature(String consumerToken) {
    return parseTokenParts(consumerToken).headerWithPayload();
  }

  public static String getClaimFromToken(String consumerToken, String claimName) {
    return getAllClaimsFromToken(consumerToken).getBody().get(claimName, String.class);
  }

  public static Jwt<?, Claims> getAllClaimsFromToken(String consumerToken) {
    String tokenWithoutSignature = getTokenWithoutSignature(consumerToken);

    try {
      return jwtParser.parseClaimsJwt(tokenWithoutSignature);
    } catch (Exception e) {
      log.error("Failed to parse consumer token", e);
      throw e;
    }
  }

  private static TokenParts parseTokenParts(String consumerToken) {
    String fullyProcessedToken = processToken(consumerToken);

    int firstDot = fullyProcessedToken.indexOf(".");
    int secondDot = fullyProcessedToken.indexOf(".", firstDot + 1);

    if (secondDot == -1) {
      throw new IllegalArgumentException("Invalid token format");
    }

    String headerAndPayload = fullyProcessedToken.substring(0, secondDot + 1);
    String signature = fullyProcessedToken.substring(secondDot + 1);

    return new TokenParts(headerAndPayload, signature);
  }

  private static @NonNull String processToken(String consumerToken) {
    if (Objects.isNull(consumerToken)) {
      throw new IllegalArgumentException("Consumer token not provided, but expected");
    }

    String trimmedConsumerToken = consumerToken.trim();

    final String BEARER_PREFIX = "Bearer ";
    if (!trimmedConsumerToken.startsWith(BEARER_PREFIX)) {
      throw new IllegalArgumentException("Invalid token format, Missing Bearer prefix");
    }

    String tokenWithoutBearer = trimmedConsumerToken.substring(BEARER_PREFIX.length());
    return tokenWithoutBearer.trim();
  }

  private record TokenParts(String headerWithPayload, String signature) {}
}

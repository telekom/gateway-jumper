// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.*;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class OauthTokenUtil {

  private static final JwtParser jwtParser =
      Jwts.parserBuilder().setAllowedClockSkewSeconds(3600).build();

  // Private constructor to prevent instantiation
  private OauthTokenUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static String getTokenWithoutSignature(String consumerToken) {
    return parseTokenParts(consumerToken).headerWithPayload();
  }

  public static String getSignature(String consumerToken) {
    return parseTokenParts(consumerToken).signature();
  }

  private static TokenParts parseTokenParts(String consumerToken) {
    if (Objects.isNull(consumerToken)) {
      throw new IllegalArgumentException("Consumer token not provided, but expected");
    }

    String trimmedConsumerToken = consumerToken.trim();

    int spaceIndex = trimmedConsumerToken.indexOf(" ");
    if (spaceIndex == -1) {
      throw new IllegalArgumentException("Invalid token format, Missing Bearer prefix");
    }

    String token = trimmedConsumerToken.substring(spaceIndex + 1);

    int firstDot = token.indexOf(".");
    int secondDot = token.indexOf(".", firstDot + 1);

    if (secondDot == -1) {
      throw new IllegalArgumentException("Invalid token format");
    }

    String headerAndPayload = token.substring(0, secondDot + 1);
    String signature = token.substring(secondDot + 1);

    return new TokenParts(headerAndPayload, signature);
  }

  public static String getClaimFromToken(String consumerToken, String claimName) {
    String consumerTokenWithoutSignature = getTokenWithoutSignature(consumerToken);
    return getAllClaimsFromToken(consumerTokenWithoutSignature)
        .getBody()
        .get(claimName, String.class);
  }

  public static Jwt<?, Claims> getAllClaimsFromToken(String consumerToken) {

    try {
      return jwtParser.parseClaimsJwt(consumerToken);
    } catch (Exception e) {
      log.error("Failed to parse consumer token", e);
      throw e;
    }
  }

  private record TokenParts(String headerWithPayload, String signature) {}
}

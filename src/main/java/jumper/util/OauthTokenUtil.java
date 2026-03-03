// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
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

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String trimmedConsumerToken = consumerToken.trim();

    int spaceIndex = trimmedConsumerToken.indexOf(" ");
    if (spaceIndex == -1) {
      throw new IllegalStateException("Invalid token format, Missing Bearer prefix");
    }

    String token = trimmedConsumerToken.substring(spaceIndex + 1).trim();

    int headerPayloadSeperatorIndex = token.indexOf(".");

    int payloadSignatureSeperatorIndex = token.indexOf(".", headerPayloadSeperatorIndex + 1);
    if (payloadSignatureSeperatorIndex == -1) {
      throw new IllegalStateException("Invalid token format");
    }

    return token.substring(0, payloadSignatureSeperatorIndex + 1);
  }

  public static String getSignature(String consumerToken) {

    if (Objects.isNull(consumerToken)) {
      throw new IllegalStateException("Consumer token not provided, but expected");
    }

    String trimmedConsumerToken = consumerToken.trim();

    int spaceIndex = trimmedConsumerToken.indexOf(" ");
    if (spaceIndex == -1) {
      throw new IllegalStateException("Invalid token format, Missing Bearer prefix");
    }

    String token = trimmedConsumerToken.substring(spaceIndex + 1).trim();

    int headerPayloadSeperatorIndex = token.indexOf(".");
    int payloadSignatureSeperatorIndex = token.indexOf(".", headerPayloadSeperatorIndex + 1);
    if (payloadSignatureSeperatorIndex == -1) {
      throw new IllegalStateException("Invalid token format");
    }

    return token.substring(payloadSignatureSeperatorIndex + 1);
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
    } catch (SignatureException e) {
      log.error("SignatureException", e);
    } catch (ExpiredJwtException e) {
      log.error("ExpiredJwtException", e);
    } catch (UnsupportedJwtException e) {
      log.error("UnsupportedJwtException", e);
    } catch (MalformedJwtException e) {
      log.error("MalformedJwtException", e);
    } catch (IllegalArgumentException e) {
      log.error("IllegalArgumentException", e);
    }
    throw new IllegalStateException("Was not able to parse consumer token");
  }
}

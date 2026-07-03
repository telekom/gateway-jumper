// SPDX-FileCopyrightText: 2023-2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import io.jsonwebtoken.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
public final class OauthTokenUtil {

  private static final JwtParser jwtParser =
      Jwts.parser().unsecured().clockSkewSeconds(3600).build();

  // jjwt >= 0.12 only parses unsecured JWTs whose header declares "alg":"none".
  // The consumer token is a signed JWT whose claims we read without verifying the
  // signature, so we strip the signature and swap in an unsecured header for parsing.
  private static final String UNSECURED_HEADER =
      Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));

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

    // Parse the claims with an unsecured header (jjwt requires "alg":"none"), but keep
    // the original header so callers still observe the token's real algorithm/type.
    int firstDot = tokenWithoutSignature.indexOf('.');
    String originalHeader = tokenWithoutSignature.substring(0, firstDot);
    String unsecuredToken = UNSECURED_HEADER + tokenWithoutSignature.substring(firstDot);

    try {
      Claims claims = jwtParser.parseUnsecuredClaims(unsecuredToken).getPayload();
      return new UnverifiedJwt(parseHeader(originalHeader), claims);
    } catch (Exception e) {
      log.error("Failed to parse consumer token", e);
      throw e;
    }
  }

  private static Header parseHeader(String base64UrlHeader) {
    try {
      byte[] json = Base64.getUrlDecoder().decode(base64UrlHeader);
      @SuppressWarnings("unchecked")
      Map<String, Object> values = ObjectMapperUtil.getInstance().readValue(json, Map.class);
      return new MapHeader(values);
    } catch (Exception e) {
      throw new MalformedJwtException("Unable to read JWT header", e);
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
    if (!trimmedConsumerToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      throw new IllegalArgumentException("Invalid token format, Missing Bearer prefix");
    }

    String tokenWithoutBearer = trimmedConsumerToken.substring(BEARER_PREFIX.length());
    return tokenWithoutBearer.trim();
  }

  private record TokenParts(String headerWithPayload, String signature) {}

  /** Minimal {@link Header} backed by the decoded header map of the original token. */
  private static final class MapHeader extends LinkedHashMap<String, Object> implements Header {

    private MapHeader(Map<String, Object> values) {
      super(values);
    }

    @Override
    public String getType() {
      return (String) get("typ");
    }

    @Override
    public String getContentType() {
      return (String) get("cty");
    }

    @Override
    public String getAlgorithm() {
      return (String) get("alg");
    }

    @Override
    public String getCompressionAlgorithm() {
      return (String) get("zip");
    }
  }

  /** Carries the original (unverified) header together with the parsed claims. */
  private record UnverifiedJwt(Header header, Claims claims) implements Jwt<Header, Claims> {

    @Override
    public Header getHeader() {
      return header;
    }

    @Override
    public Claims getBody() {
      return claims;
    }

    @Override
    public Claims getPayload() {
      return claims;
    }

    @Override
    public <T> T accept(JwtVisitor<T> visitor) {
      return visitor.visit(this);
    }
  }
}

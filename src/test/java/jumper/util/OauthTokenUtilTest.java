// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.util.OauthTokenUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OauthTokenUtilTest {

  private static final String CLAIM_NAME_KEY = "name";
  private static final String CLAIM_NAME_VALUE = "test user";
  private static final String CLAIM_SCOPE_KEY = "scope";
  private static final String CLAIM_SCOPE_VALUE = "dev";
  private static final String CLAIM_ISS_KEY = "iss";
  private static final String CLAIM_ISS_VALUE = "Narvi";
  private static final String CLAIM_SUB_KEY = "sub";
  private static final String CLAIM_SUB_VALUE = "tuser";

  // ---------------------------------------------------------------------------
  // Token providers
  // ---------------------------------------------------------------------------

  /** All token variants that parseTokenParts must accept as valid. */
  static Stream<Arguments> provideValidTestTokens() {
    return Stream.of(
        Arguments.of(getTestToken(true), "standard Bearer prefix"),
        Arguments.of("bearer " + getTestToken(false), "lowercase bearer prefix"),
        Arguments.of(getTestTokenWithMultipleBlanks(), "multiple spaces after Bearer"),
        Arguments.of(getTestTokenWithLeadingSpaces(), "leading spaces before Bearer"));
  }

  /**
   * Tokens that lack a proper Bearer prefix or JWT structure and must trigger
   * IllegalArgumentException from parseTokenParts.
   */
  static Stream<Arguments> provideInvalidBearerTokens() {
    String rawJwt = getTestToken(false);

    String noSignatureToken = getTestToken(true).trim();
    noSignatureToken = noSignatureToken.substring(0, noSignatureToken.lastIndexOf("."));

    return Stream.of(
        Arguments.of("", "empty token"),
        Arguments.of(null, "null token"),
        Arguments.of(rawJwt, "raw JWT without Bearer prefix"),
        Arguments.of(noSignatureToken, "Bearer token with signature stripped"));
  }

  // ---------------------------------------------------------------------------
  // Tests for getTokenWithoutSignature
  // ---------------------------------------------------------------------------

  @Nested
  class getTokenWithoutSignatureTests {

    @Nested
    @DisplayName("Valid tokens")
    class ValidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return header.payload. portion from a valid JWT token")
      public void testGetTokenWithoutSignature_validToken_returnsHeaderAndPayload(
          String token, String description) {
        String normalized = token.trim().substring("Bearer ".length()).trim();
        String expected = normalized.substring(0, normalized.lastIndexOf(".") + 1);

        assertThat(getTokenWithoutSignature(token)).isEqualTo(expected);
      }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideInvalidBearerTokens")
      @DisplayName("Should throw IllegalArgumentException for")
      public void testGetTokenWithoutSignature_invalidToken_throwsException(
          String token, String description) {
        assertThrows(IllegalArgumentException.class, () -> getTokenWithoutSignature(token));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Tests for getSignature
  // ---------------------------------------------------------------------------

  @Nested
  class getSignatureTests {

    @Nested
    @DisplayName("Valid tokens")
    class ValidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return the signature part from a valid JWT token")
      public void testGetSignature_validToken_returnsSignature(String token, String description) {
        String normalized = token.trim().substring("Bearer ".length()).trim();
        String expected = normalized.substring(normalized.lastIndexOf(".") + 1);

        assertThat(getSignature(token)).isEqualTo(expected);
      }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideInvalidBearerTokens")
      @DisplayName("Should throw IllegalArgumentException for")
      public void testGetSignature_invalidToken_throwsException(String token, String description) {
        assertThrows(IllegalArgumentException.class, () -> getSignature(token));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Tests for getClaimFromToken
  // ---------------------------------------------------------------------------

  @Nested
  class getClaimFromTokenTests {

    @Nested
    @DisplayName("Valid tokens")
    class ValidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return custom claims from a valid token")
      public void testGetClaimFromToken_validToken_returnsCustomClaims(
          String token, String description) {
        assertThat(getClaimFromToken(token, CLAIM_NAME_KEY)).isEqualTo(CLAIM_NAME_VALUE);
        assertThat(getClaimFromToken(token, CLAIM_SCOPE_KEY)).isEqualTo(CLAIM_SCOPE_VALUE);
      }

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return standard JWT claims (sub, iss) from a valid token")
      public void testGetClaimFromToken_validToken_returnsStandardClaims(
          String token, String description) {
        assertThat(getClaimFromToken(token, CLAIM_ISS_KEY)).isEqualTo(CLAIM_ISS_VALUE);
        assertThat(getClaimFromToken(token, CLAIM_SUB_KEY)).isEqualTo(CLAIM_SUB_VALUE);
      }

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return null for a non-existent claim name")
      public void testGetClaimFromToken_nonExistentClaim_returnsNull(
          String token, String description) {
        assertThat(getClaimFromToken(token, "nonExistentClaim")).isNull();
      }

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return null for an empty claim name")
      public void testGetClaimFromToken_emptyClaimName_returnsNull(
          String token, String description) {
        assertThat(getClaimFromToken(token, "")).isNull();
      }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideInvalidBearerTokens")
      @DisplayName("Should throw IllegalArgumentException for")
      public void testGetClaimFromToken_invalidToken_throwsException(
          String token, String description) {
        assertThrows(
            IllegalArgumentException.class, () -> getClaimFromToken(token, CLAIM_NAME_KEY));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Tests for getAllClaimsFromToken
  // ---------------------------------------------------------------------------

  @Nested
  class getAllClaimsFromTokenTests {

    @Nested
    @DisplayName("Valid tokens")
    class ValidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideValidTestTokens")
      @DisplayName("Should return all claims from a valid Bearer-prefixed JWT token")
      public void testGetAllClaimsFromToken_validToken_returnsAllClaims(
          String token, String description) {
        var claims = getAllClaimsFromToken(token);

        assertThat(claims.getHeader().getAlgorithm()).isEqualTo("none");
        assertThat(claims.getBody().get(CLAIM_NAME_KEY, String.class)).isEqualTo(CLAIM_NAME_VALUE);
        assertThat(claims.getBody().get(CLAIM_SCOPE_KEY, String.class))
            .isEqualTo(CLAIM_SCOPE_VALUE);
        assertThat(claims.getBody().getSubject()).isEqualTo(CLAIM_SUB_VALUE);
        assertThat(claims.getBody().getIssuer()).isEqualTo(CLAIM_ISS_VALUE);
      }
    }

    @Nested
    @DisplayName("Invalid tokens")
    class InvalidTokens {

      @ParameterizedTest(name = "{1}")
      @MethodSource("jumper.util.OauthTokenUtilTest#provideInvalidBearerTokens")
      @DisplayName("Should throw IllegalArgumentException for")
      public void testGetAllClaimsFromToken_invalidToken_throwsException(
          String token, String description) {
        assertThrows(IllegalArgumentException.class, () -> getAllClaimsFromToken(token));
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Token factory helpers
  // ---------------------------------------------------------------------------

  private static String getTestToken(boolean withBearerPrefix) {
    return withBearerPrefix ? "Bearer " + createToken() : createToken();
  }

  private static String getTestTokenWithMultipleBlanks() {
    return "Bearer      " + createToken();
  }

  private static String getTestTokenWithLeadingSpaces() {
    return "  Bearer " + createToken();
  }

  private static String createToken() {
    return Jwts.builder()
        .setIssuer(CLAIM_ISS_VALUE)
        .setSubject(CLAIM_SUB_VALUE)
        .claim(CLAIM_NAME_KEY, CLAIM_NAME_VALUE)
        .claim(CLAIM_SCOPE_KEY, CLAIM_SCOPE_VALUE)
        .setIssuedAt(Date.from(Instant.now()))
        .setExpiration(Date.from(Instant.now().plusSeconds(300L)))
        .signWith(
            Keys.hmacShaKeyFor(
                Base64.getDecoder().decode("Yn2kjibddFAWtnPJ2AFlL8WXmohJMCvigQggaEypa5E=")))
        .compact();
  }
}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.function.BiConsumer;
import java.util.stream.Stream;
import jumper.model.config.OauthCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class TokenCacheServiceTest {

  private static final String TOKEN_ENDPOINT = "https://idp.example.com/token";

  private TokenCacheService tokenCacheService;

  @BeforeEach
  void setUp() {
    tokenCacheService = new TokenCacheService(new ConcurrentMapCacheManager("cache-token-info"));
  }

  static Stream<Arguments> distinguishingAuthMaterial() {
    return Stream.of(
        // credential sets differing in a single auth dimension must never share a cache key
        Arguments.of(
            "refreshToken",
            (BiConsumer<OauthCredentials, OauthCredentials>)
                (a, b) -> {
                  a.setGrantType("refresh_token");
                  a.setRefreshToken("refresh-a");
                  b.setGrantType("refresh_token");
                  b.setRefreshToken("refresh-b");
                }),
        Arguments.of(
            "clientKey",
            (BiConsumer<OauthCredentials, OauthCredentials>)
                (a, b) -> {
                  a.setGrantType("client_credentials");
                  a.setClientKey("key-a");
                  b.setGrantType("client_credentials");
                  b.setClientKey("key-b");
                }),
        Arguments.of(
            "password",
            (BiConsumer<OauthCredentials, OauthCredentials>)
                (a, b) -> {
                  a.setGrantType("password");
                  a.setUsername("user");
                  a.setPassword("pass-a");
                  b.setGrantType("password");
                  b.setUsername("user");
                  b.setPassword("pass-b");
                }),
        Arguments.of(
            "grantType",
            (BiConsumer<OauthCredentials, OauthCredentials>)
                (a, b) -> {
                  a.setGrantType("client_credentials");
                  a.setClientId("id");
                  a.setClientSecret("secret");
                  b.setGrantType("password");
                  b.setClientId("id");
                  b.setClientSecret("secret");
                }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("distinguishingAuthMaterial")
  void generateTokenCacheKey_distinguishesAuthMaterial(
      String dimension, BiConsumer<OauthCredentials, OauthCredentials> arrange) {
    // arrange
    OauthCredentials first = new OauthCredentials();
    OauthCredentials second = new OauthCredentials();
    arrange.accept(first, second);

    // act
    String firstKey = tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, first);
    String secondKey = tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, second);

    // assert
    assertNotEquals(firstKey, secondKey, "credentials differing in " + dimension + " collided");
  }

  @Test
  void generateTokenCacheKey_identicalCredentialsShareKey() {
    // arrange
    OauthCredentials first = credentials();
    OauthCredentials second = credentials();

    // act & assert
    assertEquals(
        tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, first),
        tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, second));
  }

  @Test
  void generateTokenCacheKey_adjacentFieldsDoNotBlur() {
    // arrange: without a delimiter between hashed fields, "ab"+"c" and "a"+"bc" would collide
    OauthCredentials first = new OauthCredentials();
    first.setGrantType("client_credentials");
    first.setClientId("ab");
    first.setClientSecret("c");
    OauthCredentials second = new OauthCredentials();
    second.setGrantType("client_credentials");
    second.setClientId("a");
    second.setClientSecret("bc");

    // act & assert
    assertNotEquals(
        tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, first),
        tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, second));
  }

  private static OauthCredentials credentials() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setGrantType("client_credentials");
    credentials.setClientId("id");
    credentials.setClientSecret("secret");
    credentials.setScopes("scope");
    return credentials;
  }
}

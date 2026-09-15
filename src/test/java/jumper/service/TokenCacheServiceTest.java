// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import jumper.config.OauthTokenFetchProperties;
import jumper.model.TokenInfo;
import jumper.model.config.OauthCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class TokenCacheServiceTest {

  private static final String TOKEN_KEY = "token-key";
  private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

  private TokenCacheService tokenCacheService;

  @BeforeEach
  void setUp() {
    tokenCacheService = createTokenCacheService();
  }

  @Test
  void lookupInsideRefreshWindow_returnsTokenWithoutEvictingIt() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(20));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    var firstLookup = tokenCacheService.findServableToken(TOKEN_KEY);
    var secondLookup = tokenCacheService.findServableToken(TOKEN_KEY);

    assertThat(firstLookup).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isTrue();
    assertThat(secondLookup).containsSame(token);
  }

  @Test
  void lookupTokenWithoutExpiry_returnsFreshServableToken() {
    TokenInfo token = new TokenInfo();
    token.setAccessToken("access-token");
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isFalse();
  }

  @Test
  void lookupAboveRefreshWindow_returnsFreshToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(60));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isFalse();
  }

  @Test
  void lookupAtOrBelowMinimumServeThreshold_returnsNotServableToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(5));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
  }

  @Test
  void lookupExpiredToken_returnsNotServableToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(-1));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
  }

  @Test
  void lookupAtRefreshAheadBoundary_returnsNeedsRefresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(30));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isTrue();
  }

  @Test
  void lookupJustAboveRefreshAheadBoundary_returnsFresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofMillis(30_001));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isFalse();
  }

  @Test
  void lookupAtMinimumServeBoundary_returnsNotServable() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(10));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
  }

  @Test
  void lookupJustAboveMinimumServeBoundary_returnsNeedsRefresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofMillis(10_001));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(token);
    assertThat(tokenCacheService.isExpiringSoon(token)).isTrue();
  }

  @Test
  void isServable_appliesMinimumServeThresholdToAnyToken() {
    assertThat(tokenCacheService.isServable(tokenExpiringIn(Duration.ofSeconds(10)))).isFalse();
    assertThat(tokenCacheService.isServable(tokenExpiringIn(Duration.ofMillis(10_001)))).isTrue();
    assertThat(tokenCacheService.isServable(tokenExpiringIn(Duration.ofSeconds(-1)))).isFalse();
    TokenInfo withoutExpiry = new TokenInfo();
    withoutExpiry.setAccessToken("access-token");
    assertThat(tokenCacheService.isServable(withoutExpiry)).isTrue();
  }

  @Test
  void completedFetch_savesTokenAndUnregistersItself() {
    TokenInfo refreshedToken = tokenExpiringIn(Duration.ofMinutes(5));
    var fetch = tokenCacheService.getOrCreateFetch(TOKEN_KEY, Mono.just(refreshedToken));
    assertThat(fetch.created()).isTrue();

    StepVerifier.create(fetch.publisher()).expectNext(refreshedToken).verifyComplete();

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(refreshedToken);
    assertThat(tokenCacheService.activeFetchCount()).isZero();
    tokenCacheService.evictToken(TOKEN_KEY);
    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
  }

  @Test
  void evictionDuringFetch_discardsOlderRefreshResult() {
    tokenCacheService.saveToken(TOKEN_KEY, tokenExpiringIn(Duration.ofMinutes(5)));
    Sinks.One<TokenInfo> idpResponse = Sinks.one();
    tokenCacheService.getOrCreateFetch(TOKEN_KEY, idpResponse.asMono()).publisher().subscribe();

    tokenCacheService.evictToken(TOKEN_KEY);
    idpResponse.tryEmitValue(tokenExpiringIn(Duration.ofMinutes(5))).orThrow();

    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
    assertThat(tokenCacheService.activeFetchCount()).isZero();
  }

  @Test
  void fetchStartedAfterEviction_canPopulateCacheWhileOlderFetchCannot() {
    Sinks.One<TokenInfo> oldResponse = Sinks.one();
    tokenCacheService.getOrCreateFetch(TOKEN_KEY, oldResponse.asMono()).publisher().subscribe();
    tokenCacheService.evictToken(TOKEN_KEY);
    TokenInfo newToken = tokenExpiringIn(Duration.ofMinutes(5));
    Sinks.One<TokenInfo> newResponse = Sinks.one();
    var newFetch = tokenCacheService.getOrCreateFetch(TOKEN_KEY, newResponse.asMono());
    assertThat(newFetch.created()).isTrue();
    newFetch.publisher().subscribe();

    oldResponse.tryEmitValue(tokenExpiringIn(Duration.ofMinutes(5))).orThrow();
    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).isEmpty();
    assertThat(tokenCacheService.activeFetchCount())
        .as("finishing the evicted fetch must not unregister its replacement")
        .isOne();

    newResponse.tryEmitValue(newToken).orThrow();
    assertThat(tokenCacheService.findServableToken(TOKEN_KEY)).containsSame(newToken);
    assertThat(tokenCacheService.activeFetchCount()).isZero();
  }

  @Test
  void failedFetch_isUnregisteredBeforeWaitersObserveTheError() {
    var fetch =
        tokenCacheService.getOrCreateFetch(
            TOKEN_KEY, Mono.error(new IllegalStateException("IDP unavailable")));

    StepVerifier.create(fetch.publisher())
        .expectErrorSatisfies(error -> assertThat(tokenCacheService.activeFetchCount()).isZero())
        .verify();
  }

  @Test
  void concurrentSelection_reusesExistingFetch() {
    var created = tokenCacheService.getOrCreateFetch(TOKEN_KEY, Mono.never());
    var reused = tokenCacheService.getOrCreateFetch(TOKEN_KEY, Mono.never());

    assertThat(created.created()).isTrue();
    assertThat(reused.created()).isFalse();
    assertThat(reused.publisher()).isSameAs(created.publisher());
  }

  @Test
  void oauthCacheKeySeparatesUsersSharingClientCredentials() {
    OauthCredentials first = passwordCredentials("first-user", "first-password");
    OauthCredentials second = passwordCredentials("second-user", "second-password");

    assertThat(tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", first))
        .isNotEqualTo(
            tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", second));
  }

  @Test
  void oauthCacheKeyIncludesEveryRequestIdentityField() {
    OauthCredentials base = passwordCredentials("user", "password");
    String baseKey = tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", base);

    OauthCredentials changedGrant = passwordCredentials("user", "password");
    changedGrant.setGrantType("refresh_token");
    changedGrant.setRefreshToken("refresh-token");
    OauthCredentials changedKey = passwordCredentials("user", "password");
    changedKey.setClientKey("client-key");
    OauthCredentials changedScope = passwordCredentials("user", "password");
    changedScope.setScopes("different-scope");

    assertThat(
            tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", changedGrant))
        .isNotEqualTo(baseKey);
    assertThat(tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", changedKey))
        .isNotEqualTo(baseKey);
    assertThat(
            tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", changedScope))
        .isNotEqualTo(baseKey);
    assertThat(baseKey).doesNotContain("user", "password", "client-secret");
  }

  @Test
  void equivalentClientCredentialsRequestsShareCacheKey() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId("client");
    credentials.setClientSecret("secret");
    credentials.setScopes("scope");
    credentials.setGrantType("client_credentials");
    credentials.setTokenRequest("basic");

    assertThat(
            tokenCacheService.generateTokenCacheKey("https://idp.example.com/token", credentials))
        .isEqualTo(
            tokenCacheService.generateTokenCacheKey(
                "https://idp.example.com/token", "client", "secret", "scope"));
  }

  private TokenCacheService createTokenCacheService() {
    CacheManager cacheManager = mock(CacheManager.class);
    when(cacheManager.getCache("cache-token-info"))
        .thenReturn(new ConcurrentMapCache("cache-token-info"));
    return new TokenCacheService(
        cacheManager,
        new OauthTokenFetchProperties(
            Duration.ofSeconds(2),
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            1,
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            DataSize.ofKilobytes(8),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            Duration.ofSeconds(5)),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private TokenInfo tokenExpiringIn(Duration duration) {
    TokenInfo token = new TokenInfo();
    token.setAccessToken("access-token");
    token.setExpiration(Date.from(NOW.plus(duration)));
    return token;
  }

  private OauthCredentials passwordCredentials(String username, String password) {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId("shared-client");
    credentials.setClientSecret("client-secret");
    credentials.setUsername(username);
    credentials.setPassword(password);
    credentials.setScopes("scope");
    credentials.setGrantType("password");
    return credentials;
  }
}

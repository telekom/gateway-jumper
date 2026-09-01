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
import jumper.service.TokenCacheService.Freshness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.util.unit.DataSize;
import reactor.core.publisher.Mono;

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

    var firstLookup = tokenCacheService.lookup(TOKEN_KEY);
    var secondLookup = tokenCacheService.lookup(TOKEN_KEY);

    assertThat(firstLookup.token()).isSameAs(token);
    assertThat(firstLookup.freshness()).isEqualTo(Freshness.NEEDS_REFRESH);
    assertThat(secondLookup.token()).isSameAs(token);
  }

  @Test
  void lookupTokenWithoutExpiry_returnsFreshServableToken() {
    TokenInfo token = new TokenInfo();
    token.setAccessToken("access-token");
    tokenCacheService.saveToken(TOKEN_KEY, token);

    var lookup = tokenCacheService.lookup(TOKEN_KEY);

    assertThat(lookup.servable()).isTrue();
    assertThat(lookup.needsRefresh()).isFalse();
    assertThat(lookup.token()).isSameAs(token);
  }

  @Test
  void lookupAboveRefreshWindow_returnsFreshToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(60));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).freshness()).isEqualTo(Freshness.FRESH);
  }

  @Test
  void lookupAtOrBelowMinimumServeThreshold_returnsNotServableToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(5));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    var lookup = tokenCacheService.lookup(TOKEN_KEY);

    assertThat(lookup.servable()).isFalse();
    assertThat(lookup.token()).isNull();
    assertThat(tokenCacheService.lookup(TOKEN_KEY).token()).isNull();
  }

  @Test
  void lookupExpiredToken_returnsNotServableToken() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(-1));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).servable()).isFalse();
  }

  @Test
  void lookupAtRefreshAheadBoundary_returnsNeedsRefresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(30));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).freshness()).isEqualTo(Freshness.NEEDS_REFRESH);
  }

  @Test
  void lookupJustAboveRefreshAheadBoundary_returnsFresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofMillis(30_001));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).freshness()).isEqualTo(Freshness.FRESH);
  }

  @Test
  void lookupAtMinimumServeBoundary_returnsNotServable() {
    TokenInfo token = tokenExpiringIn(Duration.ofSeconds(10));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).servable()).isFalse();
  }

  @Test
  void lookupJustAboveMinimumServeBoundary_returnsNeedsRefresh() {
    TokenInfo token = tokenExpiringIn(Duration.ofMillis(10_001));
    tokenCacheService.saveToken(TOKEN_KEY, token);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).freshness()).isEqualTo(Freshness.NEEDS_REFRESH);
  }

  @Test
  void refreshBeforeEviction_isRemovedByLaterEviction() {
    TokenInfo refreshedToken = tokenExpiringIn(Duration.ofMinutes(5));
    Mono<TokenInfo> fetch = Mono.just(refreshedToken);
    assertThat(tokenCacheService.getOrCreateFetch(TOKEN_KEY, fetch).created()).isTrue();

    assertThat(tokenCacheService.saveTokenIfFetchMatches(TOKEN_KEY, fetch, refreshedToken))
        .isTrue();
    tokenCacheService.completeFetch(TOKEN_KEY, fetch);
    tokenCacheService.evictToken(TOKEN_KEY);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).servable()).isFalse();
    assertThat(tokenCacheService.activeFetchCount()).isZero();
  }

  @Test
  void evictionBeforeRefresh_discardsOlderRefreshResult() {
    tokenCacheService.saveToken(TOKEN_KEY, tokenExpiringIn(Duration.ofMinutes(5)));
    Mono<TokenInfo> oldFetch = Mono.never();
    tokenCacheService.getOrCreateFetch(TOKEN_KEY, oldFetch);

    tokenCacheService.evictToken(TOKEN_KEY);
    boolean saved =
        tokenCacheService.saveTokenIfFetchMatches(
            TOKEN_KEY, oldFetch, tokenExpiringIn(Duration.ofMinutes(5)));
    tokenCacheService.completeFetch(TOKEN_KEY, oldFetch);

    assertThat(saved).isFalse();
    assertThat(tokenCacheService.lookup(TOKEN_KEY).servable()).isFalse();
    assertThat(tokenCacheService.activeFetchCount()).isZero();
  }

  @Test
  void fetchStartedAfterEviction_canPopulateCacheWhileOlderFetchCannot() {
    Mono<TokenInfo> oldFetch = Mono.never();
    tokenCacheService.getOrCreateFetch(TOKEN_KEY, oldFetch);
    tokenCacheService.evictToken(TOKEN_KEY);
    TokenInfo newToken = tokenExpiringIn(Duration.ofMinutes(5));
    Mono<TokenInfo> newFetch = Mono.just(newToken);
    tokenCacheService.getOrCreateFetch(TOKEN_KEY, newFetch);

    assertThat(
            tokenCacheService.saveTokenIfFetchMatches(
                TOKEN_KEY, oldFetch, tokenExpiringIn(Duration.ofMinutes(5))))
        .isFalse();
    assertThat(tokenCacheService.saveTokenIfFetchMatches(TOKEN_KEY, newFetch, newToken)).isTrue();
    tokenCacheService.completeFetch(TOKEN_KEY, oldFetch);
    assertThat(tokenCacheService.activeFetchCount()).isOne();
    tokenCacheService.completeFetch(TOKEN_KEY, newFetch);

    assertThat(tokenCacheService.lookup(TOKEN_KEY).token()).isSameAs(newToken);
    assertThat(tokenCacheService.activeFetchCount()).isZero();
    tokenCacheService.evictToken(TOKEN_KEY);
    assertThat(tokenCacheService.activeFetchCount()).isZero();
  }

  @Test
  void concurrentSelection_reusesExistingFetch() {
    Mono<TokenInfo> first = Mono.never();
    Mono<TokenInfo> second = Mono.never();

    var created = tokenCacheService.getOrCreateFetch(TOKEN_KEY, first);
    var reused = tokenCacheService.getOrCreateFetch(TOKEN_KEY, second);

    assertThat(created.publisher()).isSameAs(first);
    assertThat(created.created()).isTrue();
    assertThat(reused.publisher()).isSameAs(first);
    assertThat(reused.created()).isFalse();
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

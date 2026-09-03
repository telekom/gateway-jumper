// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Ticker;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import jumper.config.OauthTokenFetchProperties;
import jumper.exception.TokenFetchUnavailableException;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.service.TokenCacheService.FetchSelection;
import jumper.service.TokenCacheService.Freshness;
import jumper.service.TokenCacheService.TokenLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class TokenFetchServiceTest {

  private static final String TOKEN_ENDPOINT = "https://idp.example.com/token";
  private static final String CLIENT_ID = "test-client";
  private static final String CLIENT_SECRET = "test-secret";
  private static final String TOKEN_CACHE_KEY = "test-cache-key";

  private TokenCacheService tokenCacheService;
  private TokenGeneratorService tokenGeneratorService;
  private TokenFetchService tokenFetchService;
  private SimpleMeterRegistry meterRegistry;
  private TokenFetchMetrics tokenFetchMetrics;

  private AtomicInteger idpCallCount;

  @BeforeEach
  void setUp() {
    tokenCacheService = mock(TokenCacheService.class);
    tokenGeneratorService = mock(TokenGeneratorService.class);
    meterRegistry = new SimpleMeterRegistry();
    tokenFetchMetrics = new TokenFetchMetrics(meterRegistry);
    idpCallCount = new AtomicInteger(0);

    when(tokenCacheService.generateTokenCacheKey(anyString(), anyString(), anyString(), any()))
        .thenReturn(TOKEN_CACHE_KEY);
    when(tokenCacheService.lookup(anyString()))
        .thenReturn(new TokenLookup(null, Freshness.NOT_SERVABLE));
    ConcurrentHashMap<String, Mono<TokenInfo>> activeFetches = new ConcurrentHashMap<>();
    when(tokenCacheService.getOrCreateFetch(anyString(), any()))
        .thenAnswer(
            invocation -> {
              String tokenKey = invocation.getArgument(0);
              Mono<TokenInfo> candidate = invocation.getArgument(1);
              boolean[] created = {false};
              Mono<TokenInfo> selected =
                  activeFetches.computeIfAbsent(
                      tokenKey,
                      ignored -> {
                        created[0] = true;
                        return candidate;
                      });
              return new FetchSelection(selected, created[0]);
            });
    when(tokenCacheService.saveTokenIfFetchMatches(anyString(), any(), any()))
        .thenAnswer(
            invocation ->
                activeFetches.get(invocation.getArgument(0)) == invocation.getArgument(1));
    doAnswer(
            invocation -> {
              activeFetches.remove(invocation.getArgument(0), invocation.getArgument(1));
              return null;
            })
        .when(tokenCacheService)
        .completeFetch(anyString(), any());

    WebClient webClient = mockWebClient(createTokenInfo(3600), Duration.ZERO);

    tokenFetchService = createTokenFetchService(webClient);
  }

  @Test
  void singleRequest_cacheMiss_fetchesTokenFromIdp() {
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(
            token -> {
              assertThat(token.getAccessToken()).isEqualTo("mocked-access-token");
              assertThat(idpCallCount.get()).isEqualTo(1);
            })
        .verifyComplete();

    verify(tokenCacheService)
        .saveTokenIfFetchMatches(eq(TOKEN_CACHE_KEY), any(), any(TokenInfo.class));
  }

  @Test
  void tokenWithoutExpiresIn_usesAccessTokenExpiration() {
    Date tokenExpiration = jwtDate(Instant.now().plusSeconds(300));
    TokenInfo responseToken = tokenWithoutExpiresIn(jwtExpiringAt(tokenExpiration));
    tokenFetchService = createTokenFetchService(mockWebClient(responseToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getExpiration()).isEqualTo(tokenExpiration))
        .verifyComplete();
  }

  @Test
  void tokenWithoutExpiresIn_usesExpiredAccessTokenExpiration() {
    Date tokenExpiration = jwtDate(Instant.now().minusSeconds(7200));
    TokenInfo responseToken = tokenWithoutExpiresIn(jwtExpiringAt(tokenExpiration));
    tokenFetchService = createTokenFetchService(mockWebClient(responseToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getExpiration()).isEqualTo(tokenExpiration))
        .verifyComplete();
  }

  @Test
  void expiresInTakesPrecedenceOverAccessTokenExpiration() {
    TokenInfo responseToken = tokenWithoutExpiresIn(jwtExpiringAt(Instant.now().plusSeconds(300)));
    responseToken.setExpiresIn(120);
    Date responseExpiration = responseToken.getExpiration();
    tokenFetchService = createTokenFetchService(mockWebClient(responseToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getExpiration()).isEqualTo(responseExpiration))
        .verifyComplete();
  }

  @Test
  void jwtWithoutExpiration_keepsDefaultCacheLifetime() {
    TokenInfo responseToken = tokenWithoutExpiresIn(Jwts.builder().subject("subject").compact());
    tokenFetchService = createTokenFetchService(mockWebClient(responseToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getExpiration()).isNull())
        .verifyComplete();
  }

  @Test
  void opaqueTokenWithoutExpiresIn_keepsDefaultCacheLifetime() {
    TokenInfo responseToken = tokenWithoutExpiresIn("opaque-access-token");
    tokenFetchService = createTokenFetchService(mockWebClient(responseToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getExpiration()).isNull())
        .verifyComplete();
  }

  @Test
  void singleRequest_cacheHit_doesNotCallIdp() {
    TokenInfo cachedToken = createTokenInfo(3600);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.FRESH));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(
            token -> {
              assertThat(token.getAccessToken()).isEqualTo("mocked-access-token");
              assertThat(idpCallCount.get()).isEqualTo(0);
            })
        .verifyComplete();

    verify(tokenCacheService, never()).saveToken(anyString(), any(TokenInfo.class));
  }

  @Test
  void internalMeshRequest_fetchesFromProviderIdentityProviderAndCachesToken() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setInternalTokenEndpoint("https://idp.example.com/auth/realms/provider");
    config.setClientId(CLIENT_ID);
    config.setClientSecret(CLIENT_SECRET);
    TokenInfo cachedToken = createTokenInfo(3600);
    AtomicInteger cacheLookups = new AtomicInteger();
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenAnswer(
            invocation ->
                cacheLookups.getAndIncrement() == 0
                    ? new TokenLookup(null, Freshness.NOT_SERVABLE)
                    : new TokenLookup(cachedToken, Freshness.FRESH));

    // act
    TokenInfo fetchedToken = tokenFetchService.getInternalMeshAccessToken(config).block();
    TokenInfo reusedToken = tokenFetchService.getInternalMeshAccessToken(config).block();

    // assert
    assertThat(fetchedToken).isNotNull();
    assertThat(fetchedToken.getAccessToken()).isEqualTo("mocked-access-token");
    assertThat(reusedToken).isSameAs(cachedToken);
    verify(tokenCacheService, times(2))
        .generateTokenCacheKey(
            "https://idp.example.com/auth/realms/provider/protocol/openid-connect/token",
            CLIENT_ID,
            CLIENT_SECRET,
            null);
    verify(tokenCacheService)
        .saveTokenIfFetchMatches(eq(TOKEN_CACHE_KEY), any(), same(fetchedToken));
    assertThat(idpCallCount.get()).isOne();
  }

  @Test
  void concurrentRequests_cacheMiss_onlySingleIdpCall() {
    // Recreate with a slow IDP response to widen the race window
    WebClient slowWebClient = mockWebClient(createTokenInfo(3600), Duration.ofMillis(200));
    tokenFetchService = createTokenFetchService(slowWebClient);

    int concurrentRequests = 50;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
    AtomicInteger successCount = new AtomicInteger(0);
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    for (int i = 0; i < concurrentRequests; i++) {
      Thread.startVirtualThread(
          () -> {
            try {
              startLatch.await();
              tokenFetchService
                  .getAccessTokenWithClientCredentials(
                      TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
                  .doOnNext(token -> successCount.incrementAndGet())
                  .block(Duration.ofSeconds(5));
            } catch (Throwable error) {
              failures.add(error);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    // Release all threads simultaneously
    startLatch.countDown();
    await().atMost(Duration.ofSeconds(10)).until(() -> doneLatch.getCount() == 0);

    assertThat(failures).isEmpty();
    assertThat(successCount.get()).isEqualTo(concurrentRequests);
    assertThat(idpCallCount.get())
        .as("Expected exactly 1 IDP call for %d concurrent requests", concurrentRequests)
        .isEqualTo(1);
  }

  @Test
  void concurrentRequests_differentKeys_makesSeparateIdpCalls() {
    String tokenKey1 = "key-zone-A";
    String tokenKey2 = "key-zone-B";

    when(tokenCacheService.generateTokenCacheKey(
            eq("https://zone-a.example.com/token"), anyString(), anyString(), any()))
        .thenReturn(tokenKey1);
    when(tokenCacheService.generateTokenCacheKey(
            eq("https://zone-b.example.com/token"), anyString(), anyString(), any()))
        .thenReturn(tokenKey2);
    WebClient slowWebClient = mockWebClient(createTokenInfo(3600), Duration.ofMillis(100));
    tokenFetchService = createTokenFetchService(slowWebClient);

    Mono<TokenInfo> zoneA =
        tokenFetchService.getAccessTokenWithClientCredentials(
            "https://zone-a.example.com/token", CLIENT_ID, CLIENT_SECRET, null);
    Mono<TokenInfo> zoneB =
        tokenFetchService.getAccessTokenWithClientCredentials(
            "https://zone-b.example.com/token", CLIENT_ID, CLIENT_SECRET, null);

    StepVerifier.create(Mono.zip(zoneA, zoneB))
        .assertNext(
            tuple -> {
              assertThat(tuple.getT1().getAccessToken()).isEqualTo("mocked-access-token");
              assertThat(tuple.getT2().getAccessToken()).isEqualTo("mocked-access-token");
              assertThat(idpCallCount.get())
                  .as("Expected 2 separate IDP calls for 2 different zones")
                  .isEqualTo(2);
            })
        .verifyComplete();
  }

  @Test
  void afterCompletedRequest_nextRequestMakesNewIdpCall() {
    // First request
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(idpCallCount.get()).isEqualTo(1);

    // Second request (cache still empty — simulates eviction)
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(idpCallCount.get())
        .as("After the first in-flight completes, a new request should trigger a fresh IDP call")
        .isEqualTo(2);
  }

  @Test
  void failedIdpCall_cleansUpInFlightEntry_allowsRetry() {
    // Wire up a WebClient that fails first, then succeeds on the next call
    WebClient webClient = mockFailThenSucceedWebClient(createTokenInfo(3600));
    tokenFetchService = createTokenFetchService(webClient);

    // First request fails
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectError()
        .verify(Duration.ofSeconds(5));

    // Second request on the SAME instance succeeds — proves the in-flight entry was cleaned up
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getAccessToken()).isEqualTo("mocked-access-token"))
        .verifyComplete();
  }

  @Test
  void tokenInsideRefreshWindow_isServedWhileSingleBackgroundRefreshRuns() {
    TokenInfo cachedToken = createTokenInfo(20);
    TokenInfo refreshedToken = createTokenInfo(3600);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    tokenFetchService =
        createTokenFetchService(mockWebClient(refreshedToken, Duration.ofMillis(200)));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNext(cachedToken)
        .verifyComplete();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(refreshedToken));
    assertThat(idpCallCount).hasValue(1);
  }

  @Test
  void concurrentRequestsInsideRefreshWindow_makeSingleIdpCall() {
    TokenInfo cachedToken = createTokenInfo(20);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    TokenInfo refreshedToken = createTokenInfo(3600);
    Sinks.One<TokenInfo> idpResponse = Sinks.one();
    tokenFetchService = createTokenFetchService(mockWebClient(idpResponse.asMono()));

    int concurrentRequests = 50;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
    List<Throwable> failures = new CopyOnWriteArrayList<>();

    for (int index = 0; index < concurrentRequests; index++) {
      Thread.startVirtualThread(
          () -> {
            try {
              startLatch.await();
              TokenInfo result =
                  tokenFetchService
                      .getAccessTokenWithClientCredentials(
                          TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
                      .block(Duration.ofSeconds(1));
              assertThat(result).isSameAs(cachedToken);
            } catch (Throwable error) {
              failures.add(error);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    await().atMost(Duration.ofSeconds(2)).until(() -> doneLatch.getCount() == 0);

    assertThat(failures).isEmpty();
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));
    assertThat(idpCallCount).hasValue(1);
    idpResponse.tryEmitValue(refreshedToken).orThrow();
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(refreshedToken));
  }

  @Test
  void oauthCredentialsTokenInsideRefreshWindow_isServedWhileRefreshRuns() {
    TokenInfo cachedToken = createTokenInfo(20);
    TokenInfo refreshedToken = createTokenInfo(3600);
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientSecret(CLIENT_SECRET);
    credentials.setGrantType("client_credentials");
    when(tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, credentials))
        .thenReturn(TOKEN_CACHE_KEY);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    tokenFetchService =
        createTokenFetchService(mockWebClient(refreshedToken, Duration.ofMillis(200)));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithOauthCredentialsObject(TOKEN_ENDPOINT, credentials))
        .expectNext(cachedToken)
        .verifyComplete();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(refreshedToken));
  }

  @Test
  void failedBackgroundRefresh_doesNotFailRequestAndIsCounted() {
    TokenInfo cachedToken = createTokenInfo(20);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    tokenFetchService = createTokenFetchService(mockFailingWebClient());

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNext(cachedToken)
        .verifyComplete();

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> assertThat(backgroundFailureCount("background_refresh_failure")).isOne());

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    assertThat(idpCallCount).hasValue(1);
    assertThat(backgroundFailureCount("background_refresh_failure")).isOne();
  }

  @Test
  void failedBackgroundRefresh_isRetriedAfterCooldownExpires() {
    TokenInfo cachedToken = createTokenInfo(20);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    AtomicLong tickerNanos = new AtomicLong();
    tokenFetchService = createTokenFetchService(mockFailingWebClient(), tickerNanos::get);

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    assertThat(idpCallCount).hasValue(1);

    tickerNanos.addAndGet(Duration.ofSeconds(5).plusNanos(1).toNanos());
    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(2));
  }

  @Test
  void backgroundRefreshCooldownStartsAgainWhenAttemptFinishes() {
    TokenInfo cachedToken = createTokenInfo(20);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    AtomicLong tickerNanos = new AtomicLong();
    Sinks.One<TokenInfo> idpResponse = Sinks.one();
    tokenFetchService =
        createTokenFetchService(mockWebClient(idpResponse.asMono()), tickerNanos::get);

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));

    tickerNanos.addAndGet(Duration.ofSeconds(5).plusNanos(1).toNanos());
    idpResponse.tryEmitValue(createTokenInfo(20)).orThrow();
    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(metricCount("success", "background")).isOne());

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    assertThat(idpCallCount).hasValue(1);
  }

  @Test
  void successfulBackgroundRefresh_isNotRepeatedBeforeMinimumInterval() {
    TokenInfo cachedToken = createTokenInfo(20);
    TokenInfo shortLivedToken = createTokenInfo(20);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    AtomicLong tickerNanos = new AtomicLong();
    tokenFetchService =
        createTokenFetchService(mockWebClient(shortLivedToken, Duration.ZERO), tickerNanos::get);

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    assertThat(idpCallCount).hasValue(1);

    tickerNanos.addAndGet(Duration.ofSeconds(5).plusNanos(1).toNanos());
    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .block(Duration.ofSeconds(1));
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(2));
  }

  @Test
  void failedBackgroundRequestConstruction_doesNotFailRequestAndIsCounted() {
    TokenInfo cachedToken = createTokenInfo(20);
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientKey("invalid-key");
    credentials.setGrantType("client_credentials");
    when(tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, credentials))
        .thenReturn(TOKEN_CACHE_KEY);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    when(tokenGeneratorService.createJwtTokenFromKey(any(), anyString(), any(), any(), anyString()))
        .thenThrow(new IllegalArgumentException("Invalid client key"));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithOauthCredentialsObject(TOKEN_ENDPOINT, credentials))
        .expectNext(cachedToken)
        .verifyComplete();

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(() -> assertThat(backgroundFailureCount("request_build_failure")).isOne());
    assertThat(idpCallCount).hasValue(0);

    tokenFetchService
        .getAccessTokenWithOauthCredentialsObject(TOKEN_ENDPOINT, credentials)
        .block(Duration.ofSeconds(1));
    assertThat(backgroundFailureCount("request_build_failure")).isOne();
  }

  @Test
  void foregroundRequestConstructionFailurePreservesOriginalError() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientKey("invalid-key");
    credentials.setGrantType("client_credentials");
    IllegalArgumentException signingFailure = new IllegalArgumentException("Invalid client key");
    when(tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, credentials))
        .thenReturn(TOKEN_CACHE_KEY);
    when(tokenGeneratorService.createJwtTokenFromKey(any(), anyString(), any(), any(), anyString()))
        .thenThrow(signingFailure);

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithOauthCredentialsObject(TOKEN_ENDPOINT, credentials))
        .expectErrorMatches(error -> error == signingFailure)
        .verify();
  }

  @Test
  void notServableCachedToken_fetchesReplacement() {
    TokenInfo cachedToken = createTokenInfo(5);
    TokenInfo refreshedToken = createTokenInfo(3600);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NOT_SERVABLE));
    tokenFetchService = createTokenFetchService(mockWebClient(refreshedToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNext(refreshedToken)
        .verifyComplete();

    assertThat(idpCallCount).hasValue(1);
  }

  @Test
  void cancellingTriggeringRequest_doesNotCancelBackgroundRefresh() {
    TokenInfo cachedToken = createTokenInfo(20);
    TokenInfo refreshedToken = createTokenInfo(3600);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    tokenFetchService =
        createTokenFetchService(mockWebClient(refreshedToken, Duration.ofMillis(200)));

    var subscription =
        tokenFetchService
            .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
            .flatMap(ignored -> Mono.<TokenInfo>never())
            .subscribe();
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));
    subscription.dispose();

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(refreshedToken));
  }

  @Test
  void cachedTokenIsReturnedBeforeSlowRequestConstructionCompletes() {
    TokenInfo cachedToken = createTokenInfo(20);
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientKey("client-key");
    credentials.setGrantType("client_credentials");
    when(tokenCacheService.generateTokenCacheKey(TOKEN_ENDPOINT, credentials))
        .thenReturn(TOKEN_CACHE_KEY);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(new TokenLookup(cachedToken, Freshness.NEEDS_REFRESH));
    CountDownLatch releaseSigning = new CountDownLatch(1);
    when(tokenGeneratorService.createJwtTokenFromKey(any(), anyString(), any(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              releaseSigning.await();
              return "client-assertion";
            });

    Duration elapsed =
        StepVerifier.create(
                tokenFetchService.getAccessTokenWithOauthCredentialsObject(
                    TOKEN_ENDPOINT, credentials))
            .expectNext(cachedToken)
            .verifyComplete();
    releaseSigning.countDown();

    assertThat(elapsed).isLessThan(Duration.ofMillis(250));
  }

  @Test
  void overallTimeoutCoversSlowTokenResponse() {
    TokenInfo replacement = createTokenInfo(3600);
    tokenFetchService =
        createTokenFetchService(
            mockWebClient(replacement, Duration.ofSeconds(2)),
            Duration.ofMillis(100),
            Duration.ofMillis(100));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                          assertThat(exception.getStatusCode().value()).isEqualTo(504);
                          assertThat(exception.getReason())
                              .isEqualTo(
                                  "Timeout occurred while fetching token from " + TOKEN_ENDPOINT);
                        }))
        .verify(Duration.ofSeconds(1));
  }

  @Test
  void waiterTimeoutDoesNotCancelSharedFetch() {
    TokenInfo replacement = createTokenInfo(3600);
    tokenFetchService =
        createTokenFetchService(
            mockWebClient(replacement, Duration.ofMillis(300)),
            Duration.ofSeconds(1),
            Duration.ofMillis(50));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                          assertThat(exception.getStatusCode().value()).isEqualTo(504);
                          assertThat(exception.getReason())
                              .isEqualTo("Timed out waiting for a token from " + TOKEN_ENDPOINT);
                        }))
        .verify(Duration.ofSeconds(1));

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(replacement));
    assertThat(metricCount("deadline", "foreground")).isOne();
  }

  @Test
  void lateSubscriberGetsItsOwnWaitDeadlineWhileSharedFetchContinues() throws InterruptedException {
    TokenInfo replacement = createTokenInfo(3600);
    tokenFetchService =
        createTokenFetchService(
            mockWebClient(replacement, Duration.ofMillis(400)),
            Duration.ofSeconds(1),
            Duration.ofMillis(100));

    tokenFetchService
        .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
        .subscribe(ignored -> {}, ignored -> {});
    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> assertThat(idpCallCount).hasValue(1));
    Thread.sleep(150);

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(504)))
        .verify(Duration.ofSeconds(1));

    await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> verifyTokenSaved(replacement));
    assertThat(idpCallCount).hasValue(1);
  }

  @Test
  void wrappedConnectionFailure_isRetriedOnceAndMappedToUnauthorized() {
    WebClientRequestException failure =
        new WebClientRequestException(
            new ConnectTimeoutException("Connection timed out"),
            HttpMethod.POST,
            URI.create(TOKEN_ENDPOINT),
            HttpHeaders.EMPTY);
    tokenFetchService = createTokenFetchService(mockWebClient(Mono.error(failure)));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        TokenFetchUnavailableException.class,
                        exception -> {
                          assertThat(exception.getStatusCode().value()).isEqualTo(401);
                          assertThat(exception.getReason()).startsWith("Failed to connect to ");
                        }))
        .verify(Duration.ofSeconds(2));

    assertThat(idpCallCount).hasValue(2);
    assertThat(metricCount("retry_exhausted", "foreground")).isOne();
    assertThat(
            meterRegistry
                .get("jumper.oauth.token.retries")
                .tag("mode", "foreground")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void nonRoutableEndpointWrapsConnectionFailureInWebClientRequestException() {
    Duration connectTimeout = Duration.ofMillis(100);
    tokenFetchService =
        createTokenFetchService(
            webClientWithConnectTimeout(connectTimeout),
            Duration.ofSeconds(10),
            Duration.ofSeconds(10));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                "http://192.0.2.1:81/token", CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(TokenFetchUnavailableException.class);
              assertThat(causeChainContains(error, WebClientRequestException.class)).isTrue();
              assertThat(
                      causeChainContains(error, ConnectTimeoutException.class)
                          || causeChainContains(error, ConnectException.class)
                          || causeChainContains(error, NoRouteToHostException.class))
                  .isTrue();
            })
        .verify(Duration.ofSeconds(5));
  }

  @Test
  void wrappedNoRouteToHostFailure_isMappedToUnauthorized() {
    WebClientRequestException failure =
        new WebClientRequestException(
            new NoRouteToHostException("Network is unreachable"),
            HttpMethod.POST,
            URI.create(TOKEN_ENDPOINT),
            HttpHeaders.EMPTY);
    tokenFetchService = createTokenFetchService(mockWebClient(Mono.error(failure)));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        TokenFetchUnavailableException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(401)))
        .verify(Duration.ofSeconds(2));
  }

  @Test
  void overallDeadlineBoundsUnresponsiveTokenEndpoint() {
    Duration overallTimeout = Duration.ofMillis(250);
    DisposableServer server =
        HttpServer.create().port(0).handle((request, response) -> Mono.never()).bindNow();
    try {
      tokenFetchService =
          createTokenFetchService(
              webClientWithConnectTimeout(Duration.ofSeconds(2)), overallTimeout, overallTimeout);

      Duration elapsed =
          StepVerifier.create(
                  tokenFetchService.getAccessTokenWithClientCredentials(
                      "http://localhost:" + server.port() + "/token",
                      CLIENT_ID,
                      CLIENT_SECRET,
                      null))
              .expectErrorSatisfies(
                  error ->
                      assertThat(error)
                          .isInstanceOfSatisfying(
                              ResponseStatusException.class,
                              exception ->
                                  assertThat(exception.getStatusCode())
                                      .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)))
              .verify(Duration.ofSeconds(2));

      assertThat(elapsed).isLessThan(overallTimeout.plusMillis(500));
    } finally {
      server.disposeNow();
    }
  }

  @Test
  void missingAccessTokenIsRejectedBeforeCaching() {
    TokenInfo invalidToken = createTokenInfo(3600);
    invalidToken.setAccessToken(null);
    tokenFetchService = createTokenFetchService(mockWebClient(invalidToken, Duration.ZERO));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectErrorSatisfies(
            error ->
                assertThat(error)
                    .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                            assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_ACCEPTABLE)))
        .verify();

    verify(tokenCacheService, never()).saveTokenIfFetchMatches(anyString(), any(), any());
  }

  @Test
  void metricsUseOnlyBoundedTagsAndGaugesReturnToZero() {
    TokenInfo cachedToken = createTokenInfo(3600);
    when(tokenCacheService.lookup(TOKEN_CACHE_KEY))
        .thenReturn(
            new TokenLookup(null, Freshness.NOT_SERVABLE),
            new TokenLookup(cachedToken, Freshness.FRESH));

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNextCount(1)
        .verifyComplete();
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectNext(cachedToken)
        .verifyComplete();

    assertThat(metricCount("success", "foreground")).isOne();
    assertThat(metricCount("cache_hit", "foreground")).isOne();
    assertThat(meterRegistry.get("jumper.oauth.token.fetch.active").gauge().value()).isZero();
    assertThat(meterRegistry.get("jumper.oauth.token.waiters").gauge().value()).isZero();
    assertThat(
            meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey()))
        .doesNotContain("endpoint", "client", "consumer", "token_key");
  }

  @Test
  void gaugesReturnToZeroAfterErrorAndCancelledWaiter() {
    tokenFetchService =
        createTokenFetchService(
            mockWebClient(Mono.error(new IllegalStateException("IDP unavailable"))));
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectError()
        .verify();

    TokenInfo replacement = createTokenInfo(3600);
    tokenFetchService = createTokenFetchService(mockWebClient(replacement, Duration.ofMillis(200)));
    var subscription =
        tokenFetchService
            .getAccessTokenWithClientCredentials(TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
            .subscribe();
    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              assertThat(meterRegistry.get("jumper.oauth.token.fetch.active").gauge().value())
                  .isOne();
              assertThat(meterRegistry.get("jumper.oauth.token.waiters").gauge().value()).isOne();
            });
    subscription.dispose();

    await()
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              assertThat(meterRegistry.get("jumper.oauth.token.fetch.active").gauge().value())
                  .isZero();
              assertThat(meterRegistry.get("jumper.oauth.token.waiters").gauge().value()).isZero();
            });
  }

  @Test
  void errorBodyRead_isTruncatedToConfiguredByteLimit() {
    byte[] body = "x".repeat(10_000).getBytes(StandardCharsets.UTF_8);
    ClientResponse response =
        ClientResponse.create(HttpStatus.BAD_REQUEST)
            .body(
                reactor.core.publisher.Flux.just(
                    DefaultDataBufferFactory.sharedInstance.wrap(body)))
            .build();

    StepVerifier.create(tokenFetchService.readErrorBody(response))
        .assertNext(value -> assertThat(value.getBytes(StandardCharsets.UTF_8)).hasSize(8 * 1024))
        .verifyComplete();
  }

  @Test
  void errorBodyReadFailure_preservesOriginalUpstreamError() {
    ClientResponse response =
        ClientResponse.create(HttpStatus.BAD_REQUEST)
            .body(reactor.core.publisher.Flux.error(new IllegalStateException("body read failed")))
            .build();

    Logger logger = (Logger) LoggerFactory.getLogger(TokenFetchService.class);
    Level previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    try {
      StepVerifier.create(
              tokenFetchService.handleIdpError(response, TOKEN_ENDPOINT, TOKEN_CACHE_KEY))
          .assertNext(
              error ->
                  assertThat(error)
                      .isInstanceOfSatisfying(
                          ResponseStatusException.class,
                          exception -> {
                            assertThat(exception.getStatusCode().value()).isEqualTo(401);
                            assertThat(exception.getReason())
                                .contains("original status: 400 BAD_REQUEST");
                          }))
          .verifyComplete();
    } finally {
      logger.setLevel(previousLevel);
    }
  }

  // --- helpers ---

  private TokenFetchService createTokenFetchService(WebClient webClient) {
    return createTokenFetchService(webClient, Duration.ofSeconds(5), Duration.ofSeconds(5));
  }

  private TokenFetchService createTokenFetchService(WebClient webClient, Ticker ticker) {
    return createTokenFetchService(webClient, Duration.ofSeconds(5), Duration.ofSeconds(5), ticker);
  }

  private TokenFetchService createTokenFetchService(
      WebClient webClient, Duration overallTimeout, Duration requestWaitTimeout) {
    return createTokenFetchService(
        webClient, overallTimeout, requestWaitTimeout, Ticker.systemTicker());
  }

  private TokenFetchService createTokenFetchService(
      WebClient webClient, Duration overallTimeout, Duration requestWaitTimeout, Ticker ticker) {
    return new TokenFetchService(
        webClient,
        tokenCacheService,
        tokenGeneratorService,
        tokenFetchMetrics,
        new OauthTokenFetchProperties(
            Duration.ofSeconds(2),
            overallTimeout,
            requestWaitTimeout,
            1,
            Duration.ofMillis(200),
            Duration.ofSeconds(1),
            DataSize.ofKilobytes(8),
            Duration.ofSeconds(30),
            Duration.ofSeconds(10),
            Duration.ofSeconds(5)),
        ticker);
  }

  private double backgroundFailureCount(String outcome) {
    return metricCount(outcome, "background");
  }

  private WebClient webClientWithConnectTimeout(Duration connectTimeout) {
    HttpClient httpClient =
        HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()));
    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }

  private boolean causeChainContains(Throwable error, Class<? extends Throwable> type) {
    Throwable current = error;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private double metricCount(String outcome, String mode) {
    return meterRegistry
        .get("jumper.oauth.token.fetch")
        .tags("outcome", outcome, "mode", mode)
        .counter()
        .count();
  }

  private TokenInfo createTokenInfo(int expiresInSeconds) {
    TokenInfo tokenInfo = new TokenInfo();
    tokenInfo.setAccessToken("mocked-access-token");
    tokenInfo.setExpiresIn(expiresInSeconds);
    tokenInfo.setTokenType("Bearer");
    return tokenInfo;
  }

  private TokenInfo tokenWithoutExpiresIn(String accessToken) {
    TokenInfo tokenInfo = new TokenInfo();
    tokenInfo.setAccessToken(accessToken);
    tokenInfo.setTokenType("Bearer");
    return tokenInfo;
  }

  private String jwtExpiringAt(Instant expiration) {
    return jwtExpiringAt(jwtDate(expiration));
  }

  private String jwtExpiringAt(Date expiration) {
    return Jwts.builder().expiration(expiration).compact();
  }

  private Date jwtDate(Instant instant) {
    return Date.from(Instant.ofEpochSecond(instant.getEpochSecond()));
  }

  @SuppressWarnings("unchecked")
  private WebClient mockWebClient(TokenInfo responseToken, Duration delay) {
    Mono<TokenInfo> response =
        delay.isZero() ? Mono.just(responseToken) : Mono.just(responseToken).delayElement(delay);
    return mockWebClient(response);
  }

  @SuppressWarnings("unchecked")
  private WebClient mockWebClient(Mono<TokenInfo> response) {
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(TokenInfo.class))
        .thenReturn(
            Mono.defer(
                () -> {
                  idpCallCount.incrementAndGet();
                  return response;
                }));

    return webClient;
  }

  @SuppressWarnings("unchecked")
  private WebClient mockFailThenSucceedWebClient(TokenInfo responseToken) {
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    AtomicInteger callCount = new AtomicInteger(0);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(TokenInfo.class))
        .thenReturn(
            Mono.defer(
                () -> {
                  if (callCount.getAndIncrement() == 0) {
                    return Mono.error(new RuntimeException("IDP unavailable"));
                  }
                  return Mono.just(responseToken);
                }));

    return webClient;
  }

  @SuppressWarnings("unchecked")
  private WebClient mockFailingWebClient() {
    WebClient webClient = mock(WebClient.class);
    WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
    WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
    WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
    WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(TokenInfo.class))
        .thenReturn(
            Mono.defer(
                () -> {
                  idpCallCount.incrementAndGet();
                  return Mono.error(new IllegalStateException("IDP unavailable"));
                }));

    return webClient;
  }

  private void verifyTokenSaved(TokenInfo refreshedToken) {
    verify(tokenCacheService)
        .saveTokenIfFetchMatches(eq(TOKEN_CACHE_KEY), any(), same(refreshedToken));
  }
}

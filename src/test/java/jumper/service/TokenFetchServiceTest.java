// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import jumper.model.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TokenFetchServiceTest {

  private static final String TOKEN_ENDPOINT = "https://idp.example.com/token";
  private static final String CLIENT_ID = "test-client";
  private static final String CLIENT_SECRET = "test-secret";
  private static final String TOKEN_CACHE_KEY = "test-cache-key";

  private TokenCacheService tokenCacheService;
  private TokenGeneratorService tokenGeneratorService;
  private TokenFetchService tokenFetchService;

  private AtomicInteger idpCallCount;

  @BeforeEach
  void setUp() {
    tokenCacheService = mock(TokenCacheService.class);
    tokenGeneratorService = mock(TokenGeneratorService.class);
    idpCallCount = new AtomicInteger(0);

    when(tokenCacheService.generateTokenCacheKey(anyString(), anyString(), anyString(), any()))
        .thenReturn(TOKEN_CACHE_KEY);

    WebClient webClient = mockWebClient(createTokenInfo(3600), Duration.ZERO);

    tokenFetchService = new TokenFetchService(webClient, tokenCacheService, tokenGeneratorService);
  }

  @Test
  void singleRequest_cacheMiss_fetchesTokenFromIdp() {
    when(tokenCacheService.getToken(TOKEN_CACHE_KEY)).thenReturn(Optional.empty());

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(
            token -> {
              assertThat(token.getAccessToken()).isEqualTo("mocked-access-token");
              assertThat(idpCallCount.get()).isEqualTo(1);
            })
        .verifyComplete();

    verify(tokenCacheService).saveToken(eq(TOKEN_CACHE_KEY), any(TokenInfo.class));
  }

  @Test
  void singleRequest_cacheHit_doesNotCallIdp() {
    TokenInfo cachedToken = createTokenInfo(3600);
    when(tokenCacheService.getToken(TOKEN_CACHE_KEY)).thenReturn(Optional.of(cachedToken));

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
  void concurrentRequests_cacheMiss_onlySingleIdpCall() {
    when(tokenCacheService.getToken(TOKEN_CACHE_KEY)).thenReturn(Optional.empty());

    // Recreate with a slow IDP response to widen the race window
    WebClient slowWebClient = mockWebClient(createTokenInfo(3600), Duration.ofMillis(200));
    tokenFetchService =
        new TokenFetchService(slowWebClient, tokenCacheService, tokenGeneratorService);

    int concurrentRequests = 50;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(concurrentRequests);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < concurrentRequests; i++) {
      new Thread(
              () -> {
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                tokenFetchService
                    .getAccessTokenWithClientCredentials(
                        TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null)
                    .doOnNext(token -> successCount.incrementAndGet())
                    .block(Duration.ofSeconds(5));
                doneLatch.countDown();
              })
          .start();
    }

    // Release all threads simultaneously
    startLatch.countDown();

    try {
      doneLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

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
    when(tokenCacheService.getToken(anyString())).thenReturn(Optional.empty());

    WebClient slowWebClient = mockWebClient(createTokenInfo(3600), Duration.ofMillis(100));
    tokenFetchService =
        new TokenFetchService(slowWebClient, tokenCacheService, tokenGeneratorService);

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
    when(tokenCacheService.getToken(TOKEN_CACHE_KEY)).thenReturn(Optional.empty());

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
    when(tokenCacheService.getToken(TOKEN_CACHE_KEY)).thenReturn(Optional.empty());

    // Wire up a failing WebClient
    WebClient failingWebClient = mockFailingWebClient();
    tokenFetchService =
        new TokenFetchService(failingWebClient, tokenCacheService, tokenGeneratorService);

    // First request fails
    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .expectError()
        .verify(Duration.ofSeconds(5));

    // Now swap in a working WebClient and verify the in-flight map was cleaned up
    WebClient workingWebClient = mockWebClient(createTokenInfo(3600), Duration.ZERO);
    tokenFetchService =
        new TokenFetchService(workingWebClient, tokenCacheService, tokenGeneratorService);

    StepVerifier.create(
            tokenFetchService.getAccessTokenWithClientCredentials(
                TOKEN_ENDPOINT, CLIENT_ID, CLIENT_SECRET, null))
        .assertNext(token -> assertThat(token.getAccessToken()).isEqualTo("mocked-access-token"))
        .verifyComplete();
  }

  // --- helpers ---

  private TokenInfo createTokenInfo(int expiresInSeconds) {
    TokenInfo tokenInfo = new TokenInfo();
    tokenInfo.setAccessToken("mocked-access-token");
    tokenInfo.setExpiresIn(expiresInSeconds);
    tokenInfo.setTokenType("Bearer");
    return tokenInfo;
  }

  @SuppressWarnings("unchecked")
  private WebClient mockWebClient(TokenInfo responseToken, Duration delay) {
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
                  return delay.isZero()
                      ? Mono.just(responseToken)
                      : Mono.just(responseToken).delayElement(delay);
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
        .thenReturn(Mono.error(new RuntimeException("IDP unavailable")));

    return webClient;
  }
}

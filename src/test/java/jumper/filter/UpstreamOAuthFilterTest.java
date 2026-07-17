// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.stream.Stream;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.service.JumperConfigService;
import jumper.service.TokenCacheService;
import jumper.service.TokenFetchService;
import jumper.service.TokenGeneratorService;
import jumper.util.ExchangeStateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

class UpstreamOAuthFilterTest {

  private static final String CONSUMER = "some--consumer--app";
  private static final String TOKEN_ENDPOINT = "https://idp.example.com/token";

  private TokenFetchService tokenFetchService;
  private JumperConfigService jumperConfigService;
  private SimpleMeterRegistry meterRegistry;
  private GatewayFilter filter;
  private GatewayFilterChain chain;

  @BeforeEach
  void setUp() {
    tokenFetchService = mock(TokenFetchService.class);
    jumperConfigService = mock(JumperConfigService.class);
    meterRegistry = new SimpleMeterRegistry();
    UpstreamOAuthFilter upstreamOAuthFilter =
        new UpstreamOAuthFilter(
            tokenFetchService,
            mock(TokenGeneratorService.class),
            jumperConfigService,
            mock(TokenCacheService.class),
            meterRegistry);
    filter = upstreamOAuthFilter.apply(new UpstreamOAuthFilter.Config());
    chain = mock(GatewayFilterChain.class);
  }

  static Stream<Arguments> clientAuthCases() {
    return Stream.of(
        // clientId, clientSecret, clientKey, username, password, refreshToken, expected
        Arguments.of("id", "secret", null, null, null, null, true),
        Arguments.of("id", null, null, null, null, null, false),
        Arguments.of(null, "secret", null, null, null, null, false),
        Arguments.of(null, null, "key", null, null, null, true),
        Arguments.of("id", null, "key", null, null, null, true),
        Arguments.of(null, null, null, "user", "pass", null, true),
        Arguments.of(null, null, null, "user", null, null, false),
        Arguments.of(null, null, null, null, "pass", null, false),
        Arguments.of(null, null, null, null, null, "refresh", true),
        Arguments.of(null, null, null, null, null, null, false),
        Arguments.of("", "  ", "", "", "", "", false));
  }

  @ParameterizedTest
  @MethodSource("clientAuthCases")
  void hasResolvableClientAuth(
      String clientId,
      String clientSecret,
      String clientKey,
      String username,
      String password,
      String refreshToken,
      boolean expected) {
    // arrange
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(clientId);
    credentials.setClientSecret(clientSecret);
    credentials.setClientKey(clientKey);
    credentials.setUsername(username);
    credentials.setPassword(password);
    credentials.setRefreshToken(refreshToken);
    credentials.setGrantType("client_credentials");

    // act & assert
    assertEquals(expected, UpstreamOAuthFilter.hasResolvableClientAuth(credentials));
  }

  @Test
  void modernPathWithoutClientAuthRejectsBeforeIdpCall() {
    // arrange: consumer entry with grant type but no client authentication at all
    OauthCredentials consumer = new OauthCredentials();
    consumer.setGrantType("client_credentials");
    consumer.setScopes("some-scope");
    JumperConfig jc = jumperConfigWithOauth(consumer, null);

    // act & assert
    expectMissingClientAuthRejection(
        jc, "need clientId+clientSecret, clientKey, username+password, or refreshToken");
  }

  @Test
  void mergedConsumerAndDefaultBothWithoutClientAuthRejectsBeforeIdpCall() {
    // arrange: neither the consumer entry nor the default entry carries client authentication;
    // the merge inherits the default's grant type, so the modern path must still fail loud
    OauthCredentials consumer = new OauthCredentials();
    consumer.setScopes("consumer-scope");
    OauthCredentials def = new OauthCredentials();
    def.setGrantType("client_credentials");
    def.setScopes("default-scope");
    JumperConfig jc = jumperConfigWithOauth(consumer, def);

    // act & assert
    expectMissingClientAuthRejection(
        jc, "need clientId+clientSecret, clientKey, username+password, or refreshToken");
  }

  @Test
  void legacyPathWithoutClientCredentialsRejectsWithGrantTypeHint() {
    // arrange: no grant type anywhere -> legacy path, which only supports clientId+clientSecret
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);
    jc.setExternalTokenEndpoint(TOKEN_ENDPOINT);

    // act & assert
    expectMissingClientAuthRejection(
        jc,
        "need clientId+clientSecret; to authenticate with clientKey, username+password, or"
            + " refreshToken, set oauth.grantType");
  }

  private void expectMissingClientAuthRejection(JumperConfig jumperConfig, String requirement) {
    when(jumperConfigService.resolveJumperConfig(any())).thenReturn(jumperConfig);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/proxy/test").build());
    ExchangeStateManager.setOAuthFilterRequired(exchange, true);

    StepVerifier.create(filter.filter(exchange, chain))
        .expectErrorSatisfies(
            throwable -> {
              ResponseStatusException rse =
                  assertInstanceOf(ResponseStatusException.class, throwable);
              assertEquals(HttpStatus.BAD_REQUEST, rse.getStatusCode());
              assertTrue(
                  rse.getReason().contains(requirement),
                  () -> "unexpected reason: " + rse.getReason());
            })
        .verify();

    verifyNoInteractions(tokenFetchService, chain);
    assertEquals(
        1.0,
        meterRegistry
            .counter("jumper.external.oauth.config.error", "reason", "missing_client_auth")
            .count());
  }

  private static JumperConfig jumperConfigWithOauth(
      OauthCredentials consumerEntry, OauthCredentials defaultEntry) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    if (consumerEntry != null) {
      oauth.put(CONSUMER, consumerEntry);
    }
    if (defaultEntry != null) {
      oauth.put(Constants.OAUTH_PROVIDER_KEY, defaultEntry);
    }
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);
    jc.setExternalTokenEndpoint(TOKEN_ENDPOINT);
    jc.setOauth(oauth);
    return jc;
  }
}

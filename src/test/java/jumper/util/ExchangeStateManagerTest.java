// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.request.HeaderConfig;
import jumper.model.request.IncomingTokenClaims;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebExchange;
import tools.jackson.databind.json.JsonMapper;

class ExchangeStateManagerTest {

  private ServerWebExchange exchange;
  private Map<String, Object> attributes;

  @BeforeAll
  static void initObjectMapper() {
    // JumperConfig (de)serialization goes through ObjectMapperUtil, which is normally populated by
    // Spring. Populate the static holder so this context-less unit test does not depend on some
    // other Spring-context test having run first (previously an order-dependent NPE).
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

  @BeforeEach
  void setUp() {
    exchange = mock(ServerWebExchange.class);
    attributes = new ConcurrentHashMap<>();
    when(exchange.getAttributes()).thenReturn(attributes);
  }

  // arrange
  @Test
  void shouldSetAndGetOAuthFilterRequired() {
    // act
    ExchangeStateManager.setOAuthFilterRequired(exchange, true);

    // assert
    assertThat(ExchangeStateManager.isOAuthFilterRequired(exchange)).isTrue();
  }

  // arrange
  @Test
  void shouldReturnFalseWhenOAuthFilterNotSet() {
    // act
    boolean result = ExchangeStateManager.isOAuthFilterRequired(exchange);

    // assert
    assertThat(result).isFalse();
  }

  // arrange
  @Test
  void shouldSetAndGetMeshRoute() {
    // act
    ExchangeStateManager.setMeshRoute(exchange, true);

    // assert
    assertThat(ExchangeStateManager.isMeshRoute(exchange)).isTrue();
  }

  // arrange
  @Test
  void shouldReturnFalseWhenMeshRouteNotSet() {
    // act
    boolean result = ExchangeStateManager.isMeshRoute(exchange);

    // assert
    assertThat(result).isFalse();
  }

  // arrange
  @Test
  void shouldSetAndGetRequestConfiguration() {
    // arrange
    JumperConfig config = new JumperConfig();
    HeaderConfig headers = headerConfig();
    IncomingTokenClaims claims = incomingTokenClaims();
    RouteListener listener = new RouteListener();

    // act
    ExchangeStateManager.setJumperConfig(exchange, config);
    ExchangeStateManager.setHeaderConfig(exchange, headers);
    ExchangeStateManager.setIncomingTokenClaims(exchange, claims);
    ExchangeStateManager.setRequestPath(exchange, "/request/path");
    ExchangeStateManager.setSelectedListener(exchange, listener);

    // assert
    assertThat(ExchangeStateManager.getJumperConfig(exchange)).containsSame(config);
    assertThat(ExchangeStateManager.getHeaderConfig(exchange)).containsSame(headers);
    assertThat(ExchangeStateManager.getIncomingTokenClaims(exchange)).containsSame(claims);
    assertThat(ExchangeStateManager.getRequestPath(exchange)).contains("/request/path");
    assertThat(ExchangeStateManager.getSelectedListener(exchange)).containsSame(listener);
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenRequestConfigurationNotSet() {
    // act & assert
    assertThat(ExchangeStateManager.getJumperConfig(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getHeaderConfig(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getIncomingTokenClaims(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getRequestPath(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getSelectedListener(exchange)).isEmpty();
  }

  // arrange
  @Test
  void shouldSetAndGetCachedRequestBody() {
    // arrange
    String requestBody = "{\"test\":\"data\"}";

    // act
    ExchangeStateManager.setCachedRequestBody(exchange, requestBody);
    Optional<String> result = ExchangeStateManager.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(requestBody);
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenRequestBodyNotSet() {
    // act
    Optional<String> result = ExchangeStateManager.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  // arrange
  @Test
  void shouldSetAndGetCachedResponseBody() {
    // arrange
    String responseBody = "{\"result\":\"success\"}";

    // act
    ExchangeStateManager.setCachedResponseBody(exchange, responseBody);
    Optional<String> result = ExchangeStateManager.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(responseBody);
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenResponseBodyNotSet() {
    // act
    Optional<String> result = ExchangeStateManager.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  private static HeaderConfig headerConfig() {
    return new HeaderConfig(
        false, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static IncomingTokenClaims incomingTokenClaims() {
    return new IncomingTokenClaims(
        "test-consumer", "subject", "issuer", null, null, null, null, null);
  }

  // arrange
  @Test
  void shouldHandleNullBodyValues() {
    // act
    ExchangeStateManager.setCachedRequestBody(exchange, null);
    ExchangeStateManager.setCachedResponseBody(exchange, null);

    // assert
    assertThat(ExchangeStateManager.getCachedRequestBody(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getCachedResponseBody(exchange)).isEmpty();
  }

  // arrange
  @Test
  void shouldOverwriteExistingValues() {
    // arrange
    ExchangeStateManager.setOAuthFilterRequired(exchange, true);
    ExchangeStateManager.setCachedRequestBody(exchange, "first");

    // act
    ExchangeStateManager.setOAuthFilterRequired(exchange, false);
    ExchangeStateManager.setCachedRequestBody(exchange, "second");

    // assert
    assertThat(ExchangeStateManager.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(ExchangeStateManager.getCachedRequestBody(exchange).get()).isEqualTo("second");
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jumper.model.config.JumperConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebExchange;

class ExchangeStateManagerTest {

  private ExchangeStateManager exchangeStateManager;
  private ServerWebExchange exchange;
  private Map<String, Object> attributes;

  @BeforeEach
  void setUp() {
    exchangeStateManager = new ExchangeStateManager();
    exchange = mock(ServerWebExchange.class);
    attributes = new ConcurrentHashMap<>();
    when(exchange.getAttributes()).thenReturn(attributes);
    when(exchange.getAttribute(anyString()))
        .thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
  }

  // arrange
  @Test
  void shouldSetAndGetOAuthFilterRequired() {
    // act
    exchangeStateManager.setOAuthFilterRequired(exchange, true);

    // assert
    assertThat(exchangeStateManager.isOAuthFilterRequired(exchange)).isTrue();
  }

  // arrange
  @Test
  void shouldReturnFalseWhenOAuthFilterNotSet() {
    // act
    boolean result = exchangeStateManager.isOAuthFilterRequired(exchange);

    // assert
    assertThat(result).isFalse();
  }

  // arrange
  @Test
  void shouldSetAndGetJumperConfig() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    config.setRealmName("test-realm");

    // act
    exchangeStateManager.setJumperConfig(exchange, config);
    Optional<JumperConfig> result = exchangeStateManager.getJumperConfig(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getConsumer()).isEqualTo("test-consumer");
    assertThat(result.get().getRealmName()).isEqualTo("test-realm");
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenJumperConfigNotSet() {
    // act
    Optional<JumperConfig> result = exchangeStateManager.getJumperConfig(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  // arrange
  @Test
  void shouldSetAndGetCachedRequestBody() {
    // arrange
    String requestBody = "{\"test\":\"data\"}";

    // act
    exchangeStateManager.setCachedRequestBody(exchange, requestBody);
    Optional<String> result = exchangeStateManager.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(requestBody);
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenRequestBodyNotSet() {
    // act
    Optional<String> result = exchangeStateManager.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  // arrange
  @Test
  void shouldSetAndGetCachedResponseBody() {
    // arrange
    String responseBody = "{\"result\":\"success\"}";

    // act
    exchangeStateManager.setCachedResponseBody(exchange, responseBody);
    Optional<String> result = exchangeStateManager.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(responseBody);
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenResponseBodyNotSet() {
    // act
    Optional<String> result = exchangeStateManager.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  // arrange
  @Test
  void shouldClearAllCustomState() {
    // arrange
    exchangeStateManager.setOAuthFilterRequired(exchange, true);
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    exchangeStateManager.setJumperConfig(exchange, config);
    exchangeStateManager.setCachedRequestBody(exchange, "request");
    exchangeStateManager.setCachedResponseBody(exchange, "response");

    // Set a framework attribute to ensure it's not cleared
    URI frameworkUri = URI.create("http://framework.test");
    exchange
        .getAttributes()
        .put(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                .GATEWAY_REQUEST_URL_ATTR,
            frameworkUri);

    // act
    exchangeStateManager.clearCustomState(exchange);

    // assert
    assertThat(exchangeStateManager.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(exchangeStateManager.getJumperConfig(exchange)).isEmpty();
    assertThat(exchangeStateManager.getCachedRequestBody(exchange)).isEmpty();
    assertThat(exchangeStateManager.getCachedResponseBody(exchange)).isEmpty();

    // Verify framework attribute was not cleared
    assertThat(
            (Object)
                exchange.getAttribute(
                    org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                        .GATEWAY_REQUEST_URL_ATTR))
        .isEqualTo(frameworkUri);
  }

  // arrange
  @Test
  void shouldHandleNullBodyValues() {
    // act
    exchangeStateManager.setCachedRequestBody(exchange, null);
    exchangeStateManager.setCachedResponseBody(exchange, null);

    // assert
    assertThat(exchangeStateManager.getCachedRequestBody(exchange)).isEmpty();
    assertThat(exchangeStateManager.getCachedResponseBody(exchange)).isEmpty();
  }

  // arrange
  @Test
  void shouldOverwriteExistingValues() {
    // arrange
    exchangeStateManager.setOAuthFilterRequired(exchange, true);
    exchangeStateManager.setCachedRequestBody(exchange, "first");

    // act
    exchangeStateManager.setOAuthFilterRequired(exchange, false);
    exchangeStateManager.setCachedRequestBody(exchange, "second");

    // assert
    assertThat(exchangeStateManager.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(exchangeStateManager.getCachedRequestBody(exchange).get()).isEqualTo("second");
  }
}

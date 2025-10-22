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

  private ServerWebExchange exchange;
  private Map<String, Object> attributes;

  @BeforeEach
  void setUp() {
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
  void shouldSetAndGetJumperConfig() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    config.setRealmName("test-realm");

    // act
    ExchangeStateManager.setJumperConfig(exchange, config);
    Optional<JumperConfig> result = ExchangeStateManager.getJumperConfig(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getConsumer()).isEqualTo("test-consumer");
    assertThat(result.get().getRealmName()).isEqualTo("test-realm");
  }

  // arrange
  @Test
  void shouldReturnEmptyOptionalWhenJumperConfigNotSet() {
    // act
    Optional<JumperConfig> result = ExchangeStateManager.getJumperConfig(exchange);

    // assert
    assertThat(result).isEmpty();
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

  // arrange
  @Test
  void shouldClearAllCustomState() {
    // arrange
    ExchangeStateManager.setOAuthFilterRequired(exchange, true);
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    ExchangeStateManager.setJumperConfig(exchange, config);
    ExchangeStateManager.setCachedRequestBody(exchange, "request");
    ExchangeStateManager.setCachedResponseBody(exchange, "response");

    // Set a framework attribute to ensure it's not cleared
    URI frameworkUri = URI.create("http://framework.test");
    exchange
        .getAttributes()
        .put(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                .GATEWAY_REQUEST_URL_ATTR,
            frameworkUri);

    // act
    ExchangeStateManager.clearCustomState(exchange);

    // assert
    assertThat(ExchangeStateManager.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(ExchangeStateManager.getJumperConfig(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getCachedRequestBody(exchange)).isEmpty();
    assertThat(ExchangeStateManager.getCachedResponseBody(exchange)).isEmpty();

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

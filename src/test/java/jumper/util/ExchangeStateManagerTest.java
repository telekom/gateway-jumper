// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jumper.model.config.JumperConfig;
import jumper.service.JumperConfigService;
import jumper.service.ZoneHealthCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebExchange;

class ExchangeStateManagerTest {

  private ExchangeStateManager testInstance;

  private ServerWebExchange exchange;
  private Map<String, Object> attributes;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final JsonConverter jsonConverter = new JsonConverter(objectMapper);
  private final JumperConfigService jumperConfigService =
      new JumperConfigService(mock(ZoneHealthCheckService.class), jsonConverter);

  @BeforeEach
  void setUp() {
    exchange = mock(ServerWebExchange.class);
    attributes = new ConcurrentHashMap<>();
    when(exchange.getAttributes()).thenReturn(attributes);
    when(exchange.getAttribute(anyString()))
        .thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));

    testInstance = new ExchangeStateManager(jsonConverter, jumperConfigService);
  }

  @Test
  void shouldSetAndGetOAuthFilterRequired() {
    // act
    testInstance.setOAuthFilterRequired(exchange, true);

    // assert
    assertThat(testInstance.isOAuthFilterRequired(exchange)).isTrue();
  }

  @Test
  void shouldReturnFalseWhenOAuthFilterNotSet() {
    // act
    boolean result = testInstance.isOAuthFilterRequired(exchange);

    // assert
    assertThat(result).isFalse();
  }

  @Test
  void shouldSetAndGetJumperConfig() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    config.setRealmName("test-realm");

    // act
    testInstance.setJumperConfig(exchange, config);
    Optional<JumperConfig> result = testInstance.getJumperConfig(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get().getConsumer()).isEqualTo("test-consumer");
    assertThat(result.get().getRealmName()).isEqualTo("test-realm");
  }

  @Test
  void shouldReturnEmptyOptionalWhenJumperConfigNotSet() {
    // act
    Optional<JumperConfig> result = testInstance.getJumperConfig(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  void shouldSetAndGetCachedRequestBody() {
    // arrange
    String requestBody = "{\"test\":\"data\"}";

    // act
    testInstance.setCachedRequestBody(exchange, requestBody);
    Optional<String> result = testInstance.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(requestBody);
  }

  @Test
  void shouldReturnEmptyOptionalWhenRequestBodyNotSet() {
    // act
    Optional<String> result = testInstance.getCachedRequestBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  void shouldSetAndGetCachedResponseBody() {
    // arrange
    String responseBody = "{\"result\":\"success\"}";

    // act
    testInstance.setCachedResponseBody(exchange, responseBody);
    Optional<String> result = testInstance.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(responseBody);
  }

  @Test
  void shouldReturnEmptyOptionalWhenResponseBodyNotSet() {
    // act
    Optional<String> result = testInstance.getCachedResponseBody(exchange);

    // assert
    assertThat(result).isEmpty();
  }

  @Test
  void shouldClearAllCustomState() {
    // arrange
    testInstance.setOAuthFilterRequired(exchange, true);
    JumperConfig config = new JumperConfig();
    config.setConsumer("test-consumer");
    testInstance.setJumperConfig(exchange, config);
    testInstance.setCachedRequestBody(exchange, "request");
    testInstance.setCachedResponseBody(exchange, "response");

    // Set a framework attribute to ensure it's not cleared
    URI frameworkUri = URI.create("http://framework.test");
    exchange
        .getAttributes()
        .put(
            org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                .GATEWAY_REQUEST_URL_ATTR,
            frameworkUri);

    // act
    testInstance.clearCustomState(exchange);

    // assert
    assertThat(testInstance.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(testInstance.getJumperConfig(exchange)).isEmpty();
    assertThat(testInstance.getCachedRequestBody(exchange)).isEmpty();
    assertThat(testInstance.getCachedResponseBody(exchange)).isEmpty();

    // Verify framework attribute was not cleared
    assertThat(
            (Object)
                exchange.getAttribute(
                    org.springframework.cloud.gateway.support.ServerWebExchangeUtils
                        .GATEWAY_REQUEST_URL_ATTR))
        .isEqualTo(frameworkUri);
  }

  @Test
  void shouldHandleNullBodyValues() {
    // act
    testInstance.setCachedRequestBody(exchange, null);
    testInstance.setCachedResponseBody(exchange, null);

    // assert
    assertThat(testInstance.getCachedRequestBody(exchange)).isEmpty();
    assertThat(testInstance.getCachedResponseBody(exchange)).isEmpty();
  }

  @Test
  void shouldOverwriteExistingValues() {
    // arrange
    testInstance.setOAuthFilterRequired(exchange, true);
    testInstance.setCachedRequestBody(exchange, "first");

    // act
    testInstance.setOAuthFilterRequired(exchange, false);
    testInstance.setCachedRequestBody(exchange, "second");

    // assert
    assertThat(testInstance.isOAuthFilterRequired(exchange)).isFalse();
    assertThat(testInstance.getCachedRequestBody(exchange).get()).isEqualTo("second");
  }
}

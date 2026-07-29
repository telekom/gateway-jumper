// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedException;

/**
 * Unit tests for {@link LoggingServerExchangeRejectedHandler}. These pin the observability contract
 * that makes firewall rejections diagnosable: a WARN log line, a span event plus reason tag, the
 * rejection recorded on the server observation, and escaping of client-controlled content.
 */
class LoggingServerExchangeRejectedHandlerTest {

  private static final String REASON =
      "The request was rejected because the parameter name \"\tu_ressource_type\" is not allowed.";

  private Tracer tracer;
  private Span span;
  private ListAppender<ILoggingEvent> logAppender;
  private ch.qos.logback.classic.Logger logger;

  @BeforeEach
  void setUp() {
    span = mock(Span.class);
    when(span.event(anyString())).thenReturn(span);
    when(span.tag(anyString(), anyString())).thenReturn(span);

    tracer = mock(Tracer.class);
    when(tracer.currentSpan()).thenReturn(span);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    logger = context.getLogger(LoggingServerExchangeRejectedHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  @DisplayName("rejection still answers 400, matching the Spring Security default")
  void rejection_returnsBadRequest() {
    // arrange
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");

    // act
    handler().handle(exchange, new ServerExchangeRejectedException(REASON)).block();

    // assert
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("rejection is logged at WARN with method, path and reason")
  void rejection_isLoggedAtWarn() {
    // arrange
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");

    // act
    handler().handle(exchange, new ServerExchangeRejectedException(REASON)).block();

    // assert
    assertThat(logAppender.list).hasSize(1);
    ILoggingEvent event = logAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("method=GET")
        .contains("path=/proxy/v1/getItems")
        .contains("is not allowed");
  }

  @Test
  @DisplayName("rejection adds an event and reason tag to the current span")
  void rejection_recordsSpanEventAndTag() {
    // arrange
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");

    // act
    handler().handle(exchange, new ServerExchangeRejectedException(REASON)).block();

    // assert
    verify(span).event(LoggingServerExchangeRejectedHandler.SPAN_EVENT);
    verify(span)
        .tag(LoggingServerExchangeRejectedHandler.SPAN_TAG_REASON, REASON.replace("\t", "\\u0009"));
  }

  @Test
  @DisplayName("rejection is recorded as the error of the current server observation")
  void rejection_setsErrorOnObservationContext() {
    // arrange
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");
    ServerRequestObservationContext observationContext =
        new ServerRequestObservationContext(
            exchange.getRequest(), exchange.getResponse(), exchange.getAttributes());
    exchange
        .getAttributes()
        .put(
            ServerRequestObservationContext.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE,
            observationContext);
    ServerExchangeRejectedException ex = new ServerExchangeRejectedException(REASON);

    // act
    handler().handle(exchange, ex).block();

    // assert
    assertThat(observationContext.getError()).isSameAs(ex);
  }

  @Test
  @DisplayName("control characters from client input are escaped to prevent log forging")
  void rejection_escapesControlCharacters() {
    // arrange: a %0a encoded parameter name yields a raw newline inside the reason
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");
    String forging = "The request was rejected because the parameter name \"\nu\" is not allowed.";

    // act
    handler().handle(exchange, new ServerExchangeRejectedException(forging)).block();

    // assert
    String logged = logAppender.list.getFirst().getFormattedMessage();
    assertThat(logged).doesNotContain("\n").contains("\\u000a");
  }

  @Test
  @DisplayName("no span bound to the request does not break rejection handling")
  void rejection_withoutCurrentSpan_doesNotFail() {
    // arrange
    when(tracer.currentSpan()).thenReturn(null);
    MockServerWebExchange exchange = exchange("/proxy/v1/getItems");

    // act
    handler().handle(exchange, new ServerExchangeRejectedException(REASON)).block();

    // assert
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(logAppender.list).hasSize(1);
    verify(span, never()).event(anyString());
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private LoggingServerExchangeRejectedHandler handler() {
    return new LoggingServerExchangeRejectedHandler(tracer);
  }

  private static MockServerWebExchange exchange(String path) {
    return MockServerWebExchange.from(
        MockServerHttpRequest.method(HttpMethod.GET, URI.create(path)).build());
  }
}

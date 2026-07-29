// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.security.web.server.firewall.HttpStatusExchangeRejectedHandler;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedException;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Makes {@link org.springframework.security.web.server.firewall.ServerWebExchangeFirewall}
 * rejections observable.
 *
 * <p>This handler keeps the upstream response behaviour (it delegates for the status code) and
 * adds:
 *
 * <ul>
 *   <li>a {@code WARN} log line carrying method, path and rejection reason,
 *   <li>a {@value #SPAN_EVENT} span event plus a {@value #SPAN_TAG_REASON} tag, and
 *   <li>the rejection recorded as the error of the current server observation, so the {@code
 *       http.server.requests} span is marked as failed even when no span is bound to the calling
 *       thread.
 * </ul>
 */
@Slf4j
public class LoggingServerExchangeRejectedHandler implements ServerExchangeRejectedHandler {

  /** Span event name recorded when the firewall rejects an exchange. */
  public static final String SPAN_EVENT = "firewall.rejected";

  /** Span tag holding the (sanitized) firewall rejection reason. */
  public static final String SPAN_TAG_REASON = "firewall.rejection.reason";

  private final Tracer tracer;
  private final ServerExchangeRejectedHandler delegate;

  public LoggingServerExchangeRejectedHandler(Tracer tracer) {
    // Default status of HttpStatusExchangeRejectedHandler is 400 BAD_REQUEST; delegating keeps the
    // response contract identical to the Spring Security default.
    this(tracer, new HttpStatusExchangeRejectedHandler());
  }

  LoggingServerExchangeRejectedHandler(Tracer tracer, ServerExchangeRejectedHandler delegate) {
    this.tracer = tracer;
    this.delegate = delegate;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, ServerExchangeRejectedException ex) {
    // defer so the log/span side effects run on subscription, in step with the delegate
    return Mono.defer(
        () -> {
          record(exchange, ex);
          return delegate.handle(exchange, ex);
        });
  }

  private void record(ServerWebExchange exchange, ServerExchangeRejectedException ex) {
    ServerHttpRequest request = exchange.getRequest();
    String reason = sanitize(ex.getMessage());

    log.warn(
        "Request rejected by security firewall: method={}, path={}, reason={}",
        request.getMethod(),
        sanitize(request.getPath().value()),
        reason);

    Span span = tracer.currentSpan();
    if (Objects.nonNull(span)) {
      span.event(SPAN_EVENT);
      span.tag(SPAN_TAG_REASON, reason);
    }

    // Independent of thread-bound tracing state: marks the server observation as errored.
    ServerRequestObservationContext.findCurrent(exchange.getAttributes())
        .ifPresent(context -> context.setError(ex));
  }

  /**
   * Escapes ISO control characters so client-controlled content cannot forge log lines or break
   * span attributes.
   *
   * @param value the raw, potentially client-controlled value
   * @return the value with every control character replaced by its {@code \\uXXXX} escape
   */
  private static String sanitize(String value) {
    if (Objects.isNull(value)) {
      return "";
    }

    StringBuilder sanitized = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (Character.isISOControl(codePoint)) {
                sanitized.append(String.format("\\u%04x", codePoint));
              } else {
                sanitized.appendCodePoint(codePoint);
              }
            });

    return sanitized.toString();
  }
}

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

  /** Marker appended to values truncated at {@link #MAX_VALUE_LENGTH}. */
  static final String TRUNCATION_MARKER = "…(truncated)";

  /** Cap for client-controlled values; the firewall message prefix is ~60 characters. */
  static final int MAX_VALUE_LENGTH = 256;

  private final Tracer tracer;
  private final ServerExchangeRejectedHandler delegate;

  public LoggingServerExchangeRejectedHandler(Tracer tracer) {
    // HttpStatusExchangeRejectedHandler defaults to 400, matching Spring Security's behaviour
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
   * Truncates and escapes a client-controlled value for logging and span tagging.
   *
   * <p>The cap matters because the firewall embeds the offending header or parameter verbatim and
   * Netty accepts up to 8 KB of it. Escaping control characters is defense in depth; the structured
   * console, OTLP and Zipkin encoders already escape them.
   */
  private static String sanitize(String value) {
    if (Objects.isNull(value)) {
      return "";
    }

    boolean truncated = value.length() > MAX_VALUE_LENGTH;
    String capped = truncated ? value.substring(0, MAX_VALUE_LENGTH) : value;

    // a lone surrogate left by the cut is not encodable as UTF-8, which OTLP requires
    if (truncated
        && !capped.isEmpty()
        && Character.isHighSurrogate(capped.charAt(capped.length() - 1))) {
      capped = capped.substring(0, capped.length() - 1);
    }

    // isISOControl only matches BMP code points, so iterating chars is sufficient
    StringBuilder escaped = new StringBuilder(capped.length());
    for (int i = 0; i < capped.length(); i++) {
      char character = capped.charAt(i);
      if (Character.isISOControl(character)) {
        escaped.append(String.format("\\u%04x", (int) character));
      } else {
        escaped.append(character);
      }
    }

    return truncated ? escaped.append(TRUNCATION_MARKER).toString() : escaped.toString();
  }
}

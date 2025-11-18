// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A filter that monitors and validates upstream connections for plaintext HTTP usage.
 *
 * <p>This allows observability of insecure plaintext connections in production and can optionally
 * block them based on the configured validation mode.
 *
 * <p>Records Prometheus metrics for plaintext HTTP usage including hostname and validation mode.
 */
@Component
@Slf4j
public class PlaintextValidationFilter
    extends AbstractGatewayFilterFactory<PlaintextValidationFilter.Config> {

  private static final String METRIC_PLAINTEXT_CONNECTION = "jumper.ssl.plaintext.connection";
  private static final int PLAINTEXT_VALIDATION_FILTER_ORDER =
      RemoveRequestHeaderFilter.REMOVE_REQUEST_HEADER_FILTER_ORDER + 1;

  @Value("${jumper.ssl.plaintext-validation-mode:insecure}")
  private String plaintextValidationMode;

  private final MeterRegistry meterRegistry;

  public PlaintextValidationFilter(MeterRegistry meterRegistry) {
    super(Config.class);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          // Early exit if validation is disabled
          if ("insecure".equalsIgnoreCase(plaintextValidationMode)) {
            return chain.filter(exchange);
          }

          URI targetUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);

          if (targetUri == null) {
            // No target URI yet, continue without validation
            return chain.filter(exchange);
          }

          String scheme = targetUri.getScheme();
          if (scheme == null) {
            // No scheme, continue without validation
            return chain.filter(exchange);
          }

          // Check if using plaintext HTTP (not HTTPS)
          if ("http".equalsIgnoreCase(scheme)) {
            return handlePlaintextConnection(exchange, chain, targetUri);
          }

          return chain.filter(exchange);
        },
        PLAINTEXT_VALIDATION_FILTER_ORDER);
  }

  /**
   * Handles detection of plaintext HTTP connection based on validation mode.
   *
   * @param exchange the server web exchange
   * @param chain the gateway filter chain
   * @param targetUri the target URI being accessed
   * @return Mono for continuing or blocking the request
   */
  private Mono<Void> handlePlaintextConnection(
      ServerWebExchange exchange,
      org.springframework.cloud.gateway.filter.GatewayFilterChain chain,
      URI targetUri) {

    String hostname = targetUri.getHost() != null ? targetUri.getHost() : "unknown";

    switch (plaintextValidationMode.toLowerCase()) {
      case "strict":
        // Block the connection
        log.error(
            "Plaintext HTTP connection blocked in strict mode. "
                + "Target: {}, Host: {}, Path: {}. "
                + "Only HTTPS connections are allowed.",
            targetUri,
            hostname,
            targetUri.getPath());

        recordPlaintextConnection(hostname, "blocked");

        exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
        return exchange
            .getResponse()
            .writeWith(
                Mono.just(
                    exchange
                        .getResponse()
                        .bufferFactory()
                        .wrap(
                            ("Plaintext HTTP connection blocked by gateway security policy. "
                                    + "Only HTTPS connections are allowed.")
                                .getBytes())));

      case "warn":
        // Log warning but allow connection
        log.warn(
            "Plaintext HTTP connection detected, but connection is allowed to proceed. "
                + "Target: {}, Host: {}, Path: {}. "
                + "Consider upgrading to HTTPS for security.",
            targetUri,
            hostname,
            targetUri.getPath());

        recordPlaintextConnection(hostname, "warned");
        return chain.filter(exchange);

      default:
        // insecure mode - allow silently without logging or metrics
        return chain.filter(exchange);
    }
  }

  /**
   * Records a plaintext HTTP connection metric.
   *
   * @param hostname the target hostname
   * @param action the action taken (blocked, warned, allowed)
   */
  private void recordPlaintextConnection(String hostname, String action) {
    List<Tag> tags = new ArrayList<>();
    tags.add(Tag.of("hostname", hostname));
    tags.add(Tag.of("protocol", "http"));
    tags.add(Tag.of("action", action));
    tags.add(Tag.of("validation_mode", plaintextValidationMode));

    meterRegistry.counter(METRIC_PLAINTEXT_CONNECTION, tags).increment();
  }

  public static class Config extends AbstractGatewayFilterFactory.NameConfig {
    // No specific configuration needed
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import java.util.Optional;
import jumper.model.config.JumperConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

/**
 * Centralized manager for ServerWebExchange custom attribute state.
 *
 * <p>This class encapsulates all custom attribute mutations on the ServerWebExchange, providing
 * type-safe access and preventing scattered state management across filters.
 *
 * <p><strong>Usage:</strong> All filters should use this manager instead of directly calling {@code
 * exchange.getAttributes().put()} or {@code exchange.getAttribute()}.
 */
@Slf4j
public class ExchangeStateManager {

  // Private constants - encapsulated implementation details
  private static final String ATTR_OAUTH_FILTER_NEEDED = "oauth_filter_needed";
  private static final String ATTR_JUMPER_CONFIG = "jumper_config";
  private static final String ATTR_CACHED_REQUEST_BODY = "cachedRequestBodyObject";
  private static final String ATTR_CACHED_RESPONSE_BODY = "cachedResponseBodyObject";

  /**
   * Sets whether the OAuth filter is required for this request.
   *
   * @param exchange the server web exchange
   * @param required true if OAuth filter should be applied
   */
  public static void setOAuthFilterRequired(ServerWebExchange exchange, boolean required) {
    log.debug("Setting OAuth filter required: {}", required);
    exchange.getAttributes().put(ATTR_OAUTH_FILTER_NEEDED, required);
  }

  /**
   * Checks whether the OAuth filter is required for this request.
   *
   * @param exchange the server web exchange
   * @return true if OAuth filter is required, false otherwise
   */
  public static boolean isOAuthFilterRequired(ServerWebExchange exchange) {
    return Optional.ofNullable(exchange.getAttributes().get(ATTR_OAUTH_FILTER_NEEDED))
        .map(val -> (Boolean) val)
        .orElse(false);
  }

  /**
   * Stores JumperConfig in the exchange for downstream filters.
   *
   * @param exchange the server web exchange
   * @param config the jumper configuration to store
   */
  public static void setJumperConfig(ServerWebExchange exchange, JumperConfig config) {
    log.debug("Setting JumperConfig for consumer: {}", config.getConsumer());
    exchange.getAttributes().put(ATTR_JUMPER_CONFIG, JumperConfig.toJsonBase64(config));
  }

  /**
   * Retrieves JumperConfig from the exchange.
   *
   * @param exchange the server web exchange
   * @return Optional containing the JumperConfig if present
   */
  public static Optional<JumperConfig> getJumperConfig(ServerWebExchange exchange) {
    return Optional.ofNullable(exchange.getAttributes().get(ATTR_JUMPER_CONFIG))
        .map(attr -> JumperConfig.fromJsonBase64((String) attr));
  }

  /**
   * Caches the request body for use in downstream filters.
   *
   * @param exchange the server web exchange
   * @param body the request body to cache (null values are not cached)
   */
  public static void setCachedRequestBody(ServerWebExchange exchange, String body) {
    if (body != null) {
      log.debug("Caching request body (length: {})", body.length());
      exchange.getAttributes().put(ATTR_CACHED_REQUEST_BODY, body);
    } else {
      log.debug("Request body is null, removing from cache");
      exchange.getAttributes().remove(ATTR_CACHED_REQUEST_BODY);
    }
  }

  /**
   * Retrieves the cached request body.
   *
   * @param exchange the server web exchange
   * @return Optional containing the cached request body if present
   */
  public static Optional<String> getCachedRequestBody(ServerWebExchange exchange) {
    return Optional.ofNullable((String) exchange.getAttributes().get(ATTR_CACHED_REQUEST_BODY));
  }

  /**
   * Caches the response body for use in downstream filters.
   *
   * @param exchange the server web exchange
   * @param body the response body to cache (null values are not cached)
   */
  public static void setCachedResponseBody(ServerWebExchange exchange, String body) {
    if (body != null) {
      log.debug("Caching response body (length: {})", body.length());
      exchange.getAttributes().put(ATTR_CACHED_RESPONSE_BODY, body);
    } else {
      log.debug("Response body is null, removing from cache");
      exchange.getAttributes().remove(ATTR_CACHED_RESPONSE_BODY);
    }
  }

  /**
   * Retrieves the cached response body.
   *
   * @param exchange the server web exchange
   * @return Optional containing the cached response body if present
   */
  public static Optional<String> getCachedResponseBody(ServerWebExchange exchange) {
    return Optional.ofNullable((String) exchange.getAttributes().get(ATTR_CACHED_RESPONSE_BODY));
  }

  /**
   * Clears all custom state from the exchange. Useful for testing.
   *
   * @param exchange the server web exchange
   */
  public static void clearCustomState(ServerWebExchange exchange) {
    log.debug("Clearing all custom exchange state");
    exchange.getAttributes().remove(ATTR_OAUTH_FILTER_NEEDED);
    exchange.getAttributes().remove(ATTR_JUMPER_CONFIG);
    exchange.getAttributes().remove(ATTR_CACHED_REQUEST_BODY);
    exchange.getAttributes().remove(ATTR_CACHED_RESPONSE_BODY);
  }
}

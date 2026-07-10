// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import java.util.Optional;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.request.HeaderConfig;
import jumper.model.request.IncomingTokenClaims;
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
  private static final String ATTR_HEADER_CONFIG = "header_config";
  private static final String ATTR_INCOMING_TOKEN_CLAIMS = "incoming_token_claims";
  private static final String ATTR_REQUEST_PATH = "request_path";
  private static final String ATTR_SELECTED_LISTENER = "selected_listener";
  private static final String ATTR_MESH_ROUTE = "meshRoute";
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
   * Sets whether the selected route is a mesh route.
   *
   * @param exchange the server web exchange
   * @param meshRoute true if the selected route targets another gateway
   */
  public static void setMeshRoute(ServerWebExchange exchange, boolean meshRoute) {
    log.debug("Setting mesh route: {}", meshRoute);
    exchange.getAttributes().put(ATTR_MESH_ROUTE, meshRoute);
  }

  /**
   * Checks whether the selected route is a mesh route.
   *
   * @param exchange the server web exchange
   * @return true if the selected route targets another gateway, false otherwise
   */
  public static boolean isMeshRoute(ServerWebExchange exchange) {
    return Optional.ofNullable(exchange.getAttributes().get(ATTR_MESH_ROUTE))
        .map(val -> (Boolean) val)
        .orElse(false);
  }

  public static void setJumperConfig(ServerWebExchange exchange, JumperConfig config) {
    exchange.getAttributes().put(ATTR_JUMPER_CONFIG, config);
  }

  public static Optional<JumperConfig> getJumperConfig(ServerWebExchange exchange) {
    return Optional.ofNullable((JumperConfig) exchange.getAttributes().get(ATTR_JUMPER_CONFIG));
  }

  public static void setHeaderConfig(ServerWebExchange exchange, HeaderConfig config) {
    exchange.getAttributes().put(ATTR_HEADER_CONFIG, config);
  }

  public static Optional<HeaderConfig> getHeaderConfig(ServerWebExchange exchange) {
    return Optional.ofNullable((HeaderConfig) exchange.getAttributes().get(ATTR_HEADER_CONFIG));
  }

  public static void setIncomingTokenClaims(
      ServerWebExchange exchange, IncomingTokenClaims claims) {
    exchange.getAttributes().put(ATTR_INCOMING_TOKEN_CLAIMS, claims);
  }

  public static Optional<IncomingTokenClaims> getIncomingTokenClaims(ServerWebExchange exchange) {
    return Optional.ofNullable(
        (IncomingTokenClaims) exchange.getAttributes().get(ATTR_INCOMING_TOKEN_CLAIMS));
  }

  public static void setRequestPath(ServerWebExchange exchange, String requestPath) {
    exchange.getAttributes().put(ATTR_REQUEST_PATH, requestPath);
  }

  public static Optional<String> getRequestPath(ServerWebExchange exchange) {
    return Optional.ofNullable((String) exchange.getAttributes().get(ATTR_REQUEST_PATH));
  }

  public static void setSelectedListener(ServerWebExchange exchange, RouteListener listener) {
    if (listener != null) {
      exchange.getAttributes().put(ATTR_SELECTED_LISTENER, listener);
    }
  }

  public static Optional<RouteListener> getSelectedListener(ServerWebExchange exchange) {
    return Optional.ofNullable(
        (RouteListener) exchange.getAttributes().get(ATTR_SELECTED_LISTENER));
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
}

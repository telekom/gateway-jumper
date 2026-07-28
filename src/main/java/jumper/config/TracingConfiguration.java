// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import jumper.Constants;
import jumper.util.ExchangeStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.headers.observation.DefaultGatewayObservationConvention;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayObservationConvention;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class TracingConfiguration {

  private static final String OBSERVATION_HTTP_CLIENT = "http.client.requests";
  private static final String OBSERVATION_HTTP_SERVER = "http.server.requests";
  private static final String OBSERVATION_GATEWAY = "spring.cloud.gateway.http.client.requests";

  @Value("${jumper.tracing.filter-param-list:}")
  List<String> queryFilterList;

  @Bean
  GatewayObservationConvention gatewayObservationConvention() {
    return new DefaultGatewayObservationConvention() {
      @Override
      @NonNull
      public String getName() {
        return OBSERVATION_GATEWAY;
      }
    };
  }

  @Bean
  public ObservationFilter customSpanNameFilter() {
    List<Pattern> compiledQueryFilterPatterns =
        queryFilterList.stream().map(Pattern::compile).toList();

    return (observationContext) -> {
      switch (observationContext.getName()) {
        case OBSERVATION_HTTP_CLIENT:
          if (observationContext instanceof ClientRequestObservationContext clientRequestContext) {
            handleClientRequestContext(clientRequestContext);
          } else {
            observationContext.setContextualName("outgoing request: unknown");
          }
          break;
        case OBSERVATION_HTTP_SERVER:
          observationContext.setContextualName("incoming request");
          break;
        case OBSERVATION_GATEWAY:
          if (observationContext instanceof GatewayContext gatewayContext) {
            handleGatewayContext(gatewayContext, compiledQueryFilterPatterns);
          }
          break;
      }
      return observationContext;
    };
  }

  private void handleGatewayContext(
      GatewayContext gatewayContext, List<Pattern> compiledQueryFilterPatterns) {
    ServerHttpRequest request = gatewayContext.getRequest();

    gatewayContext.setContextualName("outgoing request: " + getGatewaySpanName(gatewayContext));
    gatewayContext.addHighCardinalityKeyValue(
        KeyValue.of(
            "http.uri",
            filterQueryParams(request.getURI().toString(), compiledQueryFilterPatterns)));

    gatewayContext.removeLowCardinalityKeyValue("spring.cloud.gateway.route.id");
    gatewayContext.removeLowCardinalityKeyValue("spring.cloud.gateway.route.uri");

    moveLowToHighCardinality(gatewayContext, "http.method");
    moveLowToHighCardinality(gatewayContext, "http.status_code");

    String xTardisTraceId = request.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);
    appendXTardisTraceIdHeader(gatewayContext, xTardisTraceId);
  }

  private static void moveLowToHighCardinality(Observation.Context context, String key) {
    KeyValue kv = context.getLowCardinalityKeyValue(key);
    if (kv != null) {
      context.removeLowCardinalityKeyValue(key);
      context.addHighCardinalityKeyValue(kv);
    }
  }

  private static String getGatewaySpanName(GatewayContext gatewayContext) {
    return ExchangeStateManager.isMeshRoute(gatewayContext.getServerWebExchange())
        ? "gateway"
        : "provider";
  }

  private void handleClientRequestContext(ClientRequestObservationContext clientRequestContext) {
    ClientRequest request = clientRequestContext.getRequest();
    if (request == null) {
      return;
    }

    String spanName = "unknown";
    if (request.url().getPath().contains("token")) {
      spanName = "idp";
    }

    clientRequestContext.setContextualName("outgoing request: " + spanName);

    String xTardisTraceId = request.headers().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);
    appendXTardisTraceIdHeader(clientRequestContext, xTardisTraceId);
  }

  private static void appendXTardisTraceIdHeader(
      Observation.Context context, String xTardisTraceId) {
    if (xTardisTraceId != null) {
      context.addHighCardinalityKeyValue(
          KeyValue.of(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId));
    }
  }

  protected String filterQueryParams(String urlString, List<Pattern> compiledPatterns) {
    // first check, if there is something to do
    if (!urlString.contains("?") || compiledPatterns.isEmpty()) {
      return urlString;
    }

    try {
      // Determine if URL is already encoded by checking for % character
      boolean encoded = urlString.contains("%");
      UriComponents uriComponents = UriComponentsBuilder.fromUriString(urlString).build(encoded);

      MultiValueMap<String, String> filteredParams = new LinkedMultiValueMap<>();
      uriComponents
          .getQueryParams()
          .forEach(
              (key, values) -> {
                if (compiledPatterns.stream().noneMatch(p -> p.matcher(key).matches())) {
                  filteredParams.put(key, values);
                }
              });

      return UriComponentsBuilder.newInstance()
          .scheme(uriComponents.getScheme())
          .host(uriComponents.getHost())
          .port(uriComponents.getPort())
          .path(Optional.ofNullable(uriComponents.getPath()).orElse(""))
          .queryParams(filteredParams)
          .fragment(uriComponents.getFragment())
          .build()
          .toUriString();
    } catch (IllegalArgumentException e) {
      // If URL parsing fails due to invalid URL format or illegal characters in query params,
      // strip all query parameters and return the base URL to avoid breaking tracing
      log.warn(
          "Failed to parse URL for query parameter filtering: {}. Stripping all query parameters."
              + " Error: {}",
          urlString,
          e.getMessage());

      // Strip query parameters by removing everything after '?'
      int queryStartIndex = urlString.indexOf('?');
      if (queryStartIndex > 0) {
        return urlString.substring(0, queryStartIndex);
      }
      return urlString;
    }
  }
}

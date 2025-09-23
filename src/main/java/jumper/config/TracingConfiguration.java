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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class TracingConfiguration {

  @Value("${jumper.tracing.filter-param-list:}")
  List<String> queryFilterList;

  private List<Pattern> compiledQueryFilterPatterns;

  @Bean
  public ObservationFilter customSpanNameFilter() {
    // Pre-compile regex patterns for better performance
    compiledQueryFilterPatterns = queryFilterList.stream().map(Pattern::compile).toList();

    return (observationContext) -> {
      // Modify the span name
      switch (observationContext.getName()) {
        case "http.client.requests":
          String spanName = "unknown";
          if (observationContext instanceof ClientRequestObservationContext clientRequestContext) {
            ClientRequest request = clientRequestContext.getRequest();

            if (request != null && request.url().getPath().contains("token")) {
              spanName = "idp";
            } else if (request != null
                && request.headers().getFirst(Constants.HEADER_CONSUMER_TOKEN) != null) {
              spanName = "gateway";
            }

            String xTardisTraceId = request.headers().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);
            appendXTardisTraceIdHeader(clientRequestContext, xTardisTraceId);
          }
          observationContext.setContextualName("outgoing request: " + spanName);
          break;
        case "http.server.requests":
          observationContext.setContextualName("incoming request");
          break;
        case CloudGatewayPrefixedGatewayObservationConvention.NAME:
          if (observationContext instanceof GatewayContext gatewayContext) {
            ServerHttpRequest request = gatewayContext.getRequest();

            gatewayContext.setContextualName("outgoing request: provider");
            gatewayContext.addHighCardinalityKeyValue(
                KeyValue.of(
                    "http.url",
                    filterQueryParams(request.getURI().toString(), compiledQueryFilterPatterns)));

            gatewayContext.removeLowCardinalityKeyValue("spring.cloud.gateway.route.id");
            gatewayContext.removeLowCardinalityKeyValue("spring.cloud.gateway.route.uri");

            String xTardisTraceId =
                request.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID);
            appendXTardisTraceIdHeader(gatewayContext, xTardisTraceId);
          }
          break;
      }
      return observationContext;
    };
  }

  private static void appendXTardisTraceIdHeader(
      Observation.Context gatewayContext, String xTardisTraceId) {
    if (xTardisTraceId != null) {
      gatewayContext.addHighCardinalityKeyValue(
          KeyValue.of(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId));
    }
  }

  protected String filterQueryParams(String urlString, List<Pattern> compiledPatterns) {
    // first check, if there is something to do
    if (!urlString.contains("?") || compiledPatterns.isEmpty()) {
      return urlString;
    }

    var uriComponents =
        UriComponentsBuilder.fromUriString(urlString).build(urlString.contains("%"));

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
  }
}

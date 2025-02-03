// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import java.util.List;
import java.util.regex.Pattern;
import jumper.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class SleuthConfiguration {

  @Value("${spring.sleuth.filter-param-list:}")
  List<String> queryFilterList;

  // see
  // https://docs.spring.io/spring-cloud-sleuth/docs/current-SNAPSHOT/reference/html/howto.html#how-to-cutomize-http-client-spans

  @Bean(name = HttpClientResponseParser.NAME)
  HttpResponseParser httpResponseParser() {
    return ((response, context, span) ->
        span.tag("http.status_code", String.valueOf(response.statusCode())));
  }

  @Bean(name = HttpClientRequestParser.NAME)
  HttpRequestParser httpRequestParser() {
    return (request, context, span) -> {
      String xTardisTraceId = request.header(Constants.HEADER_X_TARDIS_TRACE_ID);

      String spanName;
      if (request.path().contains("token")) {
        spanName = "Idp";
      } else if (request.header(Constants.HEADER_CONSUMER_TOKEN) != null) {
        spanName = "Gateway";
      } else {
        spanName = "Provider";
      }

      span.name("Outgoing Request: " + spanName);

      span.tag("http.url", filterQueryParams(request.url(), queryFilterList));

      if (xTardisTraceId != null) {
        span.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
      }
    };
  }

  protected static String filterQueryParams(String urlString, List<String> patterns) {
    // first check, if there is something to do
    if (!urlString.contains("?") || patterns.isEmpty()) {
      return urlString;
    }

    List<Pattern> compiledPatterns = patterns.stream().map(Pattern::compile).toList();

    var uriComponents = UriComponentsBuilder.fromHttpUrl(urlString).build(urlString.contains("%"));

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
        .path(uriComponents.getPath())
        .queryParams(filteredParams)
        .fragment(uriComponents.getFragment())
        .build()
        .toUriString();
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.*;
import java.util.stream.Collectors;
import jumper.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

  private String filterQueryParams(String urlString, List<String> patterns) {
    // first check, if there is something to do
    if (!urlString.contains("?") || queryFilterList.isEmpty()) {
      return urlString;
    }

    try {
      URI uri = new URI(urlString);
      String query = uri.getQuery();
      String[] params = query.split("&");

      List<Pattern> compiledPatterns = patterns.stream().map(Pattern::compile).toList();

      String filteredParams =
          Arrays.stream(params)
              .filter(
                  param -> {
                    String[] keyValue = param.split("=");
                    return compiledPatterns.stream()
                        .noneMatch(pattern -> pattern.matcher(keyValue[0]).matches());
                  })
              .map(
                  param -> {
                    String[] keyValue = param.split("=");
                    return URLEncoder.encode(keyValue[0], StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(keyValue[1], StandardCharsets.UTF_8);
                  })
              .collect(Collectors.joining("&"));

      URI filteredUri;
      // just avoid trailing ?
      if (!filteredParams.isEmpty()) {
        filteredUri =
            new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), filteredParams, null);
      } else {
        filteredUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
      }
      return filteredUri.toString();

    } catch (URISyntaxException e) {
      // we do not want to affect processing, just log and return original url
      log.error("Problem occurred while filtering query params");
      return urlString;
    }
  }
}

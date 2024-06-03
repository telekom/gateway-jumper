// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import brave.http.HttpRequestParser;
import brave.http.HttpResponseParser;
import jumper.Constants;
import org.springframework.cloud.sleuth.instrument.web.HttpClientRequestParser;
import org.springframework.cloud.sleuth.instrument.web.HttpClientResponseParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SleuthConfiguration {

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

      span.tag("http.url", request.url());

      if (xTardisTraceId != null) {
        span.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
      }
    };
  }
}

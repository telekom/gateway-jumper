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
      String url = request.url();
      String xTardisTraceId = request.header(Constants.HEADER_X_TARDIS_TRACE_ID);

      String spanName = "Provider";
      if (request.header(Constants.HEADER_CONSUMER_TOKEN) != null) {
        spanName = "Gateway";
      }

      span.name("Outgoing Request: " + spanName);

      if (url != null) {
        span.tag("http.url", url);
      }

      if (xTardisTraceId != null) {
        span.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
      }
    };
  }
}

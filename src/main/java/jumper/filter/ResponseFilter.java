// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static net.logstash.logback.argument.StructuredArguments.value;

import java.util.Objects;
import jumper.model.response.IncomingResponse;
import jumper.model.response.JumperInfoResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class ResponseFilter extends AbstractGatewayFilterFactory<ResponseFilter.Config> {

  private final CurrentTraceContext currentTraceContext;
  private final Tracer tracer;

  public ResponseFilter(CurrentTraceContext currentTraceContext, Tracer tracer) {
    super(Config.class);
    this.currentTraceContext = currentTraceContext;
    this.tracer = tracer;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) ->
            chain
                .filter(exchange)
                .then(
                    Mono.fromRunnable(
                        () ->
                            WebFluxSleuthOperators.withSpanInScope(
                                tracer,
                                currentTraceContext,
                                exchange,
                                () -> {
                                  ServerHttpResponse response = exchange.getResponse();
                                  ServerHttpRequest request = exchange.getRequest();

                                  if (log.isDebugEnabled()) {
                                    JumperInfoResponse jumperInfoResponse =
                                        new JumperInfoResponse();
                                    IncomingResponse incomingResponse = new IncomingResponse();

                                    incomingResponse.setHost(
                                        Objects.requireNonNull(
                                                exchange.getAttribute(
                                                    ServerWebExchangeUtils
                                                        .GATEWAY_REQUEST_URL_ATTR))
                                            .toString());
                                    incomingResponse.setHttpStatusCode(
                                        Objects.requireNonNull(response.getStatusCode()).value());
                                    incomingResponse.setMethod(request.getMethodValue());
                                    incomingResponse.setRequestHeaders(
                                        request.getHeaders().toSingleValueMap());
                                    jumperInfoResponse.setIncomingResponse(incomingResponse);

                                    log.debug(
                                        "logging response: {}",
                                        value("jumperInfo", jumperInfoResponse));
                                  }

                                  long contentLength = response.getHeaders().getContentLength();

                                  Span span = tracer.currentSpan();

                                  if (Long.toString(contentLength).equals("-1")) {
                                    span.tag("message.size_response", "0");
                                  } else {
                                    span.tag("message.size_response", Long.toString(contentLength));
                                  }

                                  span.event("jrpf");
                                }))),
        RequestFilter.REQUEST_FILTER_ORDER);
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class Config extends AbstractGatewayFilterFactory.NameConfig {}
}

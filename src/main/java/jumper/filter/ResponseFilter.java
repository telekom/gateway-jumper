// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResponseFilter extends AbstractGatewayFilterFactory<ResponseFilter.Config> {

  private final Tracer tracer;

  public ResponseFilter(Tracer tracer) {
    super(Config.class);
    this.tracer = tracer;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) ->
            chain
                .filter(exchange)
                .doOnTerminate(
                    () -> {
                      if (exchange.getResponse().isCommitted()) {
                        return;
                      }
                      ServerHttpResponse response = exchange.getResponse();
                      ServerHttpRequest request = exchange.getRequest();

                      if (log.isDebugEnabled()) {
                        JumperInfoResponse jumperInfoResponse = new JumperInfoResponse();
                        IncomingResponse incomingResponse = new IncomingResponse();

                        incomingResponse.setHost(
                            Objects.requireNonNull(
                                    exchange.getAttribute(
                                        ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR))
                                .toString());
                        incomingResponse.setHttpStatusCode(
                            Objects.requireNonNull(response.getStatusCode()).value());
                        incomingResponse.setMethod(request.getMethod().name());
                        incomingResponse.setRequestHeaders(request.getHeaders().toSingleValueMap());
                        jumperInfoResponse.setIncomingResponse(incomingResponse);

                        log.atDebug()
                            .setMessage("logging response:")
                            .addKeyValue("jumperInfo", jumperInfoResponse)
                            .log();
                      }

                      long contentLength = response.getHeaders().getContentLength();

                      Span span = tracer.currentSpan();
                      if (span != null) {
                        if (contentLength == -1L) {
                          span.tag("message.size_response", "0");
                        } else {
                          span.tag("message.size_response", Long.toString(contentLength));
                        }
                        span.event("jrpf");
                      }
                    }),
        RequestFilter.REQUEST_FILTER_ORDER);
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class Config extends AbstractGatewayFilterFactory.NameConfig {}
}

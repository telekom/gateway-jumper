// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import jumper.filter.rewrite.ResponseBodyRewrite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class ResponseTransformationFilter implements GatewayFilter, Ordered {

  private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilter;
  private final ResponseBodyRewrite responseBodyRewrite;

  @Value("${spring.http.codecs.max-in-memory-size}")
  private int limit;

  public static final int RESPONSE_TRANSFORM_FILTER_ORDER =
      SpectreResponseFilter.AUTO_EVENT_RESPONSE_FILTER_ORDER - 1;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

    ServerHttpResponse response = exchange.getResponse();
    long contentLength = response.getHeaders().getContentLength();
    if (contentLength >= 0 && contentLength > limit) {
      log.warn("limit {} exceeded, will not store response payload", limit);
      return chain.filter(exchange);
    }

    return modifyResponseBodyFilter
        .apply(
            new ModifyResponseBodyGatewayFilterFactory.Config()
                .setRewriteFunction(byte[].class, byte[].class, responseBodyRewrite))
        .filter(exchange, chain);
  }

  public int getOrder() {
    return RESPONSE_TRANSFORM_FILTER_ORDER;
  }
}

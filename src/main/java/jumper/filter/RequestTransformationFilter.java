// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import jumper.filter.rewrite.RequestBodyRewrite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyRequestBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RequiredArgsConstructor
public class RequestTransformationFilter implements GatewayFilter, Ordered {
  private final ModifyRequestBodyGatewayFilterFactory modifyRequestBodyFilter;
  private final RequestBodyRewrite requestBodyRewrite;

  @Value("${spring.http.codecs.max-in-memory-size}")
  private int limit;

  public static final int REQUEST_TRANSFORM_FILTER_ORDER =
      RemoveRequestHeaderFilter.REMOVE_REQUEST_HEADER_FILTER_ORDER + 1;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

    ServerHttpRequest request = exchange.getRequest();
    long contentLength = request.getHeaders().getContentLength();
    if (contentLength >= 0 && contentLength > limit) {
      log.warn("limit {} exceeded, will not store request payload", limit);
      return chain.filter(exchange);
    }

    return modifyRequestBodyFilter
        .apply(
            new ModifyRequestBodyGatewayFilterFactory.Config()
                .setRewriteFunction(byte[].class, byte[].class, requestBodyRewrite))
        .filter(exchange, chain);
  }

  public int getOrder() {
    return REQUEST_TRANSFORM_FILTER_ORDER;
  }
}

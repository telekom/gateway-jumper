// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.rewrite;

import jumper.util.ExchangeStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResponseBodyRewrite extends AbstractBodyRewrite
    implements RewriteFunction<byte[], byte[]> {

  private final ExchangeStateManager exchangeStateManager;

  @Override
  public Publisher<byte[]> apply(ServerWebExchange exchange, byte[] originalBody) {

    if (originalBody != null) {
      exchangeStateManager.setCachedResponseBody(
          exchange,
          getBodyForContentType(
              exchange.getResponse().getHeaders().getContentType(), originalBody));
      return Mono.just(originalBody);
    } else {
      return Mono.empty();
    }
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.rewrite;

import java.util.Objects;
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
public class RequestBodyRewrite extends AbstractBodyRewrite
    implements RewriteFunction<byte[], byte[]> {

  @Override
  public Publisher<byte[]> apply(ServerWebExchange exchange, byte[] originalBody) {

    if (Objects.nonNull(originalBody)) {
      ExchangeStateManager.setCachedRequestBody(
          exchange,
          getBodyForContentType(exchange.getRequest().getHeaders().getContentType(), originalBody));
      return Mono.just(originalBody);

    } else {
      return Mono.empty();
    }
  }
}

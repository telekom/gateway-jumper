package jumper.filter.rewrite;

import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ResponseBodyRewrite extends AbstractBodyRewrite
    implements RewriteFunction<byte[], byte[]> {

  @Override
  public Publisher<byte[]> apply(ServerWebExchange exchange, byte[] originalBody) {

    if (originalBody != null) {
      exchange
          .getAttributes()
          .put(
              "cachedResponseBodyObject",
              getBodyForContentType(
                  exchange.getResponse().getHeaders().getContentType(), originalBody));
      return Mono.just(originalBody);
    } else {
      return Mono.empty();
    }
  }
}

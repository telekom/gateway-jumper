package jumper.filter.rewrite;

import java.util.Objects;
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
      exchange
          .getAttributes()
          .put(
              "cachedRequestBodyObject",
              getBodyForContentType(
                  exchange.getRequest().getHeaders().getContentType(), originalBody));
      return Mono.just(originalBody);

    } else {
      return Mono.empty();
    }
  }
}

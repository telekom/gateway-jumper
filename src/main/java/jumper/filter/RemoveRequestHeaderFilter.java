package jumper.filter;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
public class RemoveRequestHeaderFilter
    extends AbstractGatewayFilterFactory<RemoveRequestHeaderFilter.Config> {

  public static final int REMOVE_REQUEST_HEADER_FILTER_ORDER =
      RequestFilter.REQUEST_FILTER_ORDER + 1;

  public RemoveRequestHeaderFilter() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest request =
              exchange
                  .getRequest()
                  .mutate()
                  .headers(httpHeaders -> config.getHeaders().forEach(httpHeaders::remove))
                  .build();

          return chain.filter(exchange.mutate().request(request).build());
        },
        REMOVE_REQUEST_HEADER_FILTER_ORDER);
  }

  @Getter
  @Setter
  public static class Config extends AbstractGatewayFilterFactory.NameConfig {
    private Set<String> headers;
  }
}

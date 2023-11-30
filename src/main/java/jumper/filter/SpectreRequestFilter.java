package jumper.filter;

import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.service.SpectreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpectreRequestFilter
    extends AbstractGatewayFilterFactory<SpectreRequestFilter.Config> {

  private final SpectreService spectreService;

  public static final int AUTO_EVENT_REQUEST_FILTER_ORDER =
      RequestTransformationFilter.REQUEST_TRANSFORM_FILTER_ORDER + 1;

  public SpectreRequestFilter(SpectreService spectreService) {
    super(Config.class);
    this.spectreService = spectreService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest request = exchange.getRequest();

          String requestBody = exchange.getAttribute("cachedRequestBodyObject");
          log.debug(
              "Request: headers={}, payload={}",
              request.getHeaders().toSingleValueMap(),
              requestBody);

          JumperConfig jc = JumperConfig.parseConfigFrom(exchange);
          if (!jc.isListenerMatched()) {
            return chain.filter(exchange.mutate().request(request).build());
          }

          RouteListener listener = jc.getRouteListener().get(jc.getConsumer());

          spectreService.handleEvent(jc, exchange, exchange.getRequest(), listener, requestBody);
          return chain.filter(exchange);
        },
        AUTO_EVENT_REQUEST_FILTER_ORDER);
  }

  public static class Config extends AbstractGatewayFilterFactory.NameConfig {}
}

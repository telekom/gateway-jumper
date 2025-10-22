// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import java.util.Objects;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.service.SpectreService;
import jumper.util.ExchangeStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class SpectreResponseFilter
    extends AbstractGatewayFilterFactory<AbstractGatewayFilterFactory.NameConfig> {

  private final SpectreService spectreService;

  /**
   * At Order "NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1" we have the response in
   * cachedResponseBodyObject It is the Order of ModifyResponseGatewayFilter. As we have to work
   * with the response message, we are attaching to this filter with -2
   */
  public static final int AUTO_EVENT_RESPONSE_FILTER_ORDER =
      NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 2;

  public SpectreResponseFilter(SpectreService spectreService) {
    super(AbstractGatewayFilterFactory.NameConfig.class);
    this.spectreService = spectreService;
  }

  @Override
  public GatewayFilter apply(AbstractGatewayFilterFactory.NameConfig config) {
    return new OrderedGatewayFilter(
        (exchange, chain) ->
            chain
                .filter(exchange)
                .then(
                    Mono.fromRunnable(
                        () -> {
                          String responseBody =
                              ExchangeStateManager.getCachedResponseBody(exchange).orElse(null);

                          log.debug(
                              "Response: status={}, headers={}, payload={}",
                              Objects.requireNonNull(exchange.getResponse().getStatusCode())
                                  .value(),
                              exchange.getResponse().getHeaders().toSingleValueMap(),
                              responseBody);

                          // use jumperConfig passed with exchange
                          JumperConfig jumperConfig =
                              ExchangeStateManager.getJumperConfig(exchange).orElse(null);
                          if (jumperConfig.isListenerMatched()) {
                            RouteListener listener =
                                jumperConfig.getRouteListener().get(jumperConfig.getConsumer());
                            // Fire-and-forget: publish event asynchronously without blocking the
                            // response flow
                            spectreService
                                .handleEvent(
                                    jumperConfig,
                                    exchange,
                                    exchange.getResponse(),
                                    listener,
                                    responseBody)
                                .subscribe();
                          }
                        })),
        AUTO_EVENT_RESPONSE_FILTER_ORDER);
  }
}

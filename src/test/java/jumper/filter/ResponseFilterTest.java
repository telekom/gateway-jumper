// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.tracing.Tracer;
import jumper.Constants;
import jumper.service.TokenCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class ResponseFilterTest {

  private static final String TOKEN_KEY = "token-key";

  @Test
  void infrastructureFailuresDoNotEvictToken() {
    TokenCacheService tokenCache = mock(TokenCacheService.class);
    ResponseFilter filter = new ResponseFilter(mock(Tracer.class), tokenCache);

    filterResponse(filter, HttpStatus.SERVICE_UNAVAILABLE);
    filterResponse(filter, HttpStatus.GATEWAY_TIMEOUT);
    filterResponse(filter, HttpStatus.BAD_GATEWAY);

    verify(tokenCache, never()).evictToken(TOKEN_KEY);
  }

  @Test
  void authenticationFailuresStillEvictToken() {
    TokenCacheService tokenCache = mock(TokenCacheService.class);
    ResponseFilter filter = new ResponseFilter(mock(Tracer.class), tokenCache);

    filterResponse(filter, HttpStatus.UNAUTHORIZED);
    filterResponse(filter, HttpStatus.FORBIDDEN);

    verify(tokenCache, org.mockito.Mockito.times(2)).evictToken(TOKEN_KEY);
  }

  private void filterResponse(ResponseFilter filter, HttpStatus status) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/provider").build());
    exchange.getAttributes().put(Constants.GATEWAY_ATTRIBUTE_TOKEN_CACHE_KEY, TOKEN_KEY);
    GatewayFilterChain chain =
        currentExchange -> {
          currentExchange.getResponse().setStatusCode(status);
          return Mono.empty();
        };

    filter.apply(new ResponseFilter.Config()).filter(exchange, chain).block();
  }
}

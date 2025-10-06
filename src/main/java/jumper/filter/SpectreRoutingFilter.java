// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import java.net.URI;
import java.util.Objects;
import jumper.Constants;
import jumper.service.TokenGeneratorService;
import jumper.util.OauthTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.SetRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpectreRoutingFilter extends SetRequestHeaderGatewayFilterFactory {

  private final TokenGeneratorService tokenGeneratorService;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${jumper.horizon.publishEventUrl}")
  private String publishEventUrl;

  public GatewayFilter apply() {
    return (exchange, chain) -> {
      ServerHttpRequest readOnlyRequest = exchange.getRequest();
      ServerHttpRequest.Builder requestMutationBuilder = readOnlyRequest.mutate();

      // no environment info sent from kong, so we dig it from token
      String consumerToken = readOnlyRequest.getHeaders().getFirst(Constants.HEADER_AUTHORIZATION);
      String envName = Constants.DEFAULT_REALM;
      if (Objects.nonNull(consumerToken)) {
        envName =
            OauthTokenUtil.getClaimFromToken(consumerToken, "iss").replaceFirst(".*realms/", "");
      }

      // minimalistic token with correct issuer
      String spectreToken =
          "Bearer "
              + tokenGeneratorService.generateGatewayTokenForPublisher(
                  localIssuerUrl + "/" + envName, envName);

      // routing path is no longer fixed, so we set it here
      requestMutationBuilder
          .headers(httpHeaders -> httpHeaders.set(Constants.HEADER_AUTHORIZATION, spectreToken))
          // placeholder is expected just on virtual environments like qa
          .path(
              URI.create(publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName))
                  .getPath())
          .build();

      exchange
          .getAttributes()
          .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, readOnlyRequest.getURI());

      return chain.filter(exchange.mutate().request(requestMutationBuilder.build()).build());
    };
  }
}

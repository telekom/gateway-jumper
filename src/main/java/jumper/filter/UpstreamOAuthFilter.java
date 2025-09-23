// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import java.util.Objects;
import java.util.Optional;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.service.HeaderUtil;
import jumper.service.JumperConfigService;
import jumper.service.OauthTokenUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Setter
public class UpstreamOAuthFilter extends AbstractGatewayFilterFactory<UpstreamOAuthFilter.Config> {

  public static final int UPSTREAM_OAUTH_FILTER_ORDER = RequestFilter.REQUEST_FILTER_ORDER + 1;

  private final OauthTokenUtil oauthTokenUtil;
  private final JumperConfigService jumperConfigService;

  public UpstreamOAuthFilter(
      OauthTokenUtil oauthTokenUtil, JumperConfigService jumperConfigService) {
    super(Config.class);
    this.oauthTokenUtil = oauthTokenUtil;
    this.jumperConfigService = jumperConfigService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest readOnlyRequest = exchange.getRequest();

          // Early exit for localhost issuer service
          if (!oauthFilterNeeded(exchange)) {
            log.debug("Skipping UpstreamOAuthFilter for localhost issuer service");
            return chain.filter(exchange.mutate().request(readOnlyRequest).build());
          }

          log.debug("continue with UpstreamOAuthFilter");
          JumperConfig jumperConfig =
              jumperConfigService.resolveJumperConfig(exchange.getRequest());
          log.debug("JumperConfig: {}", jumperConfig);

          ServerHttpRequest.Builder requestBuilder = readOnlyRequest.mutate();

          // Reactive chain: resolve token source (mesh/external/legacy) -> set Bearer token ->
          // build request -> continue filter chain
          return resolveTokenSource(jumperConfig, requestBuilder)
              .map(tokenInfo -> setBearerToken(requestBuilder, tokenInfo))
              .map(ServerHttpRequest.Builder::build)
              .flatMap(
                  mutatedRequest -> chain.filter(exchange.mutate().request(mutatedRequest).build()))
              .onErrorResume(
                  throwable -> {
                    log.error("Error retrieving OAuth token: {}", throwable.getMessage());
                    return Mono.error(throwable);
                  });
        },
        UPSTREAM_OAUTH_FILTER_ORDER);
  }

  private boolean oauthFilterNeeded(ServerWebExchange exchange) {
    return exchange.getAttributes().get(Constants.GATEWAY_ATTRIBUTE_OAUTH_FILTER_NEEDED) != null
        && (Boolean) exchange.getAttributes().get(Constants.GATEWAY_ATTRIBUTE_OAUTH_FILTER_NEEDED);
  }

  public static class Config {}

  /** Consolidates Bearer token setting logic to avoid duplication */
  private ServerHttpRequest.Builder setBearerToken(
      ServerHttpRequest.Builder requestBuilder, TokenInfo tokenInfo) {
    HeaderUtil.addHeader(
        requestBuilder, Constants.HEADER_AUTHORIZATION, "Bearer " + tokenInfo.getAccessToken());
    return requestBuilder;
  }

  /**
   * Resolves the appropriate OAuth token source based on JumperConfig and returns token reactively
   */
  private Mono<TokenInfo> resolveTokenSource(
      JumperConfig jumperConfig, ServerHttpRequest.Builder builder) {
    if (Objects.nonNull(jumperConfig.getInternalTokenEndpoint())) {
      // Gateway-to-Gateway mesh token: JWT generated internally for inter-gateway communication
      log.debug("----------------GATEWAY MESH-------------");
      return oauthTokenUtil.getInternalMeshAccessToken(jumperConfig);

    } else if (Objects.nonNull(jumperConfig.getExternalTokenEndpoint())) {
      // External OAuth token: Fetch from external identity provider using client credentials
      log.debug("----------------EXTERNAL AUTHORIZATION-------------");
      log.debug("Remote TokenEndpoint is set to: {}", jumperConfig.getExternalTokenEndpoint());

      Optional<OauthCredentials> oauthCredentials = jumperConfig.getOauthCredentials();
      Mono<TokenInfo> tokenMono;

      if (oauthCredentials.isPresent()
          && StringUtils.isNotBlank(oauthCredentials.get().getGrantType())) {
        // Use OAuth credentials with explicit grant type (modern approach)
        log.debug("fetching token with OauthCredentials");
        tokenMono =
            oauthTokenUtil.getAccessTokenWithOauthCredentialsObject(
                jumperConfig.getExternalTokenEndpoint(), oauthCredentials.get());
      } else {
        // Fallback to legacy header-based credentials extraction
        log.debug("fetching token with legacy method");
        tokenMono = getAccessTokenFromExternalIdpLegacy(builder, jumperConfig);
      }

      return tokenMono.onErrorResume(Mono::error);

    } else {
      // No token endpoint configured - this indicates a configuration error
      return Mono.error(
          new ResponseStatusException(
              HttpStatus.UNAUTHORIZED,
              "No token endpoint configured (neither internal nor external), this should not happen"));
    }
  }

  private Mono<TokenInfo> getAccessTokenFromExternalIdpLegacy(
      ServerHttpRequest.Builder builder, JumperConfig jc) {

    String consumer = jc.getConsumer();
    String tokenEndpoint = jc.getExternalTokenEndpoint();

    Optional<OauthCredentials> oauthCredentials = jc.getOauthCredentials();

    String clientId = determineClientId(builder, jc, oauthCredentials);
    String clientSecret = determineClientSecret(builder, jc, oauthCredentials);
    String clientScope = determineClientScope(builder, jc, oauthCredentials);

    log.debug("Get token for consumer: {} with clientId: {}", consumer, clientId);
    if (Objects.nonNull(clientId) && Objects.nonNull(clientSecret)) {
      return oauthTokenUtil.getAccessTokenWithClientCredentials(
          tokenEndpoint, clientId, clientSecret, clientScope);
    } else {
      log.warn("not specified oauth config credentials for consumer: {}", consumer);
      return Mono.error(
          new ResponseStatusException(
              HttpStatus.UNAUTHORIZED,
              "Missing oauth config credentials for consumer " + consumer));
    }
  }

  private static String determineClientScope(
      ServerHttpRequest.Builder builder,
      JumperConfig jc,
      Optional<OauthCredentials> oauthCredentials) {

    String clientScope = "";
    String xSpacegateScope = jc.getXSpacegateScope();

    if (Objects.nonNull(xSpacegateScope)) {
      log.debug("Using Scope from xSpacegateScope-Header");
      clientScope = xSpacegateScope;
      HeaderUtil.removeHeader(builder, Constants.HEADER_X_SPACEGATE_SCOPE);

    } else if (oauthCredentials.isPresent()
        && StringUtils.isNotBlank(oauthCredentials.get().getScopes())) {
      clientScope = oauthCredentials.get().getScopes();

    } else {
      log.debug("Using default Provider scope");
      if (StringUtils.isNotBlank(jc.getScopes())) {
        clientScope = jc.getScopes();
      }
    }
    return clientScope;
  }

  private static String determineClientSecret(
      ServerHttpRequest.Builder builder,
      JumperConfig jc,
      Optional<OauthCredentials> oauthCredentials) {

    String clientSecret = jc.getClientSecret();
    String xSpacegateClientSecret = jc.getXSpacegateClientSecret();

    if (Objects.nonNull(xSpacegateClientSecret)) {
      log.debug("Using SubscriberClientSecret from xSpacegateClientSecret-Header");
      clientSecret = xSpacegateClientSecret;
      HeaderUtil.removeHeader(builder, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);

    } else if (oauthCredentials.isPresent()
        && StringUtils.isNotBlank(oauthCredentials.get().getClientSecret())) {
      log.debug("Using SubscriberClientSecret from JumperConfig");
      clientSecret = oauthCredentials.get().getClientSecret();

    } else {
      log.debug("Using default ProviderClientSecret");
    }
    return clientSecret;
  }

  private static String determineClientId(
      ServerHttpRequest.Builder builder,
      JumperConfig jc,
      Optional<OauthCredentials> oauthCredentials) {

    String clientId = jc.getClientId();
    String xSpacegateClientId = jc.getXSpacegateClientId();

    if (StringUtils.isNotBlank(xSpacegateClientId)) {
      log.debug("Using SubscriberClientId {} from xSpacegateClientId-Header", xSpacegateClientId);
      clientId = xSpacegateClientId;
      HeaderUtil.removeHeader(builder, Constants.HEADER_X_SPACEGATE_CLIENT_ID);

    } else if (oauthCredentials.isPresent()
        && StringUtils.isNotBlank(oauthCredentials.get().getClientId())) {

      log.debug(
          "Using SubscriberClientId {} from JumperConfig", oauthCredentials.get().getClientId());
      clientId = oauthCredentials.get().getClientId();

    } else {
      log.debug("Using default ProviderClientId {}", clientId);
    }
    return clientId;
  }
}

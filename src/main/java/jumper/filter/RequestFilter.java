// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.addOriginalRequestUrl;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jumper.Constants;
import jumper.model.config.BasicAuthCredentials;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.request.HeaderConfig;
import jumper.model.request.IncomingTokenClaims;
import jumper.service.*;
import jumper.util.BasicAuthUtil;
import jumper.util.ExchangeStateManager;
import jumper.util.HeaderUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@Component
@Slf4j
@Setter
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

  private final Tracer tracer;
  private final TokenGeneratorService tokenGeneratorService;
  private final JumperConfigResolver jumperConfigResolver;
  private final EffectiveRequestConfigResolver effectiveRequestConfigResolver;
  private final RequestHeaderParser requestHeaderParser;
  private final IncomingTokenClaimsParser incomingTokenClaimsParser;

  @Value("${jumper.issuer.url}")
  private String localIssuerBaseUrl;

  @Value("${jumper.zone.name}")
  private String currentZone;

  @Value("#{'${jumper.zone.internetFacingZones}'.toLowerCase().split(',')}")
  private List<String> internetFacingZones;

  public static final int REQUEST_FILTER_ORDER =
      RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

  public RequestFilter(
      Tracer tracer,
      TokenGeneratorService tokenGeneratorService,
      JumperConfigResolver jumperConfigResolver,
      EffectiveRequestConfigResolver effectiveRequestConfigResolver,
      RequestHeaderParser requestHeaderParser,
      IncomingTokenClaimsParser incomingTokenClaimsParser) {
    super(Config.class);
    this.tracer = tracer;
    this.tokenGeneratorService = tokenGeneratorService;
    this.jumperConfigResolver = jumperConfigResolver;
    this.effectiveRequestConfigResolver = effectiveRequestConfigResolver;
    this.requestHeaderParser = requestHeaderParser;
    this.incomingTokenClaimsParser = incomingTokenClaimsParser;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest incomingRequest = exchange.getRequest();
          addOriginalRequestUrl(exchange, incomingRequest.getURI());

          ServerHttpRequest.Builder requestMutationBuilder = incomingRequest.mutate();

          enrichCurrentSpanFromRequest(incomingRequest);

          HeaderConfig headerConfig = requestHeaderParser.readHeaderConfig(incomingRequest);
          JumperConfig jumperConfig = jumperConfigResolver.resolve(incomingRequest, headerConfig);

          // Preserve the established error order: reject missing routing before parsing the token.
          String upstreamUrl =
              effectiveRequestConfigResolver.resolveUpstreamUrl(jumperConfig, headerConfig);

          IncomingTokenClaims incomingTokenClaims =
              incomingTokenClaimsParser.parse(incomingRequest);

          ExchangeStateManager.setMeshRoute(exchange, isMeshRoute(jumperConfig, headerConfig));
          ExchangeStateManager.setJumperConfig(exchange, jumperConfig);
          ExchangeStateManager.setHeaderConfig(exchange, headerConfig);
          ExchangeStateManager.setIncomingTokenClaims(exchange, incomingTokenClaims);

          String apiBasePath =
              effectiveRequestConfigResolver.resolveApiBasePath(jumperConfig, headerConfig);
          String realmName =
              effectiveRequestConfigResolver.resolveRealmName(jumperConfig, headerConfig);
          String environment =
              effectiveRequestConfigResolver.resolveEnvironment(jumperConfig, headerConfig);

          // Incoming request path after removing Jumper's /proxy or /listener prefix.
          String forwardedPath =
              stripJumperPathPrefix(incomingRequest, config.getJumperPathPrefix());

          // Complete provider destination, including the configured upstreamUrl, forwarded path,
          // query, and fragment.
          URI finalUpstreamUri =
              buildFinalUpstreamUri(incomingRequest.getURI(), forwardedPath, upstreamUrl);

          // Full API path called by the consumer, to be recorded in the LMS token, without query or
          // fragment.
          String consumerRequestPath = apiBasePath + forwardedPath;
          ExchangeStateManager.setRequestPath(exchange, consumerRequestPath);

          if (config.getJumperPathPrefix().equals(Constants.LISTENER_ROOT_PATH_PREFIX)) {
            ExchangeStateManager.setSelectedListener(
                exchange,
                findListenerForConsumer(
                    resolveListenerConfig(incomingRequest, headerConfig, jumperConfig),
                    incomingTokenClaims.clientId()));
          }

          boolean isSecondaryFailoverRoute =
              headerConfig.hasRoutingConfigHeader()
                  && StringUtils.isEmpty(jumperConfig.getTargetZoneName());
          if (isSecondaryFailoverRoute) {
            // write audit log if needed
            AuditLogService.writeFailoverAuditLog(
                finalUpstreamUri.toString(), apiBasePath, incomingTokenClaims);

            // pass headers from config to provider
            HeaderUtil.addHeader(requestMutationBuilder, Constants.HEADER_REALM, realmName);
            HeaderUtil.addHeader(requestMutationBuilder, Constants.HEADER_ENVIRONMENT, environment);
          }

          String debugRequestScenario = "none";

          if (!upstreamUrl.startsWith(Constants.LOCALHOST_ISSUER_SERVICE)) {

            if (ExchangeStateManager.isMeshRoute(exchange)) {
              // GW-2-GW MESH TOKEN GENERATION
              log.debug("----------------GATEWAY MESH-------------");
              debugRequestScenario = "gateway-mesh";

              if (isInternetFacingZone(incomingTokenClaims.originZone())) {
                // Forward the exact incoming Authorization value; do not reconstruct the token.
                setXSpaceGateTokenHeader(
                    requestMutationBuilder,
                    HeaderUtil.getLastValueFromHeaderField(
                        incomingRequest, Constants.HEADER_AUTHORIZATION));
              }

              ExchangeStateManager.setOAuthFilterRequired(exchange, true);

            } else {
              // ALL NON MESH SCENARIOS

              if (incomingRequest.getHeaders().containsHeader(Constants.HEADER_X_TOKEN_EXCHANGE)
                  && isInternetFacingZone(currentZone)) {

                log.debug("----------------X-TOKEN-EXCHANGE HEADER-------------");
                debugRequestScenario = "x-token-exchange";

                forwardXTokenExchangeAsAuthorization(requestMutationBuilder, incomingRequest);

              } else {
                Optional<BasicAuthCredentials> basicAuthCredentials =
                    resolveBasicAuthCredentials(jumperConfig, incomingTokenClaims.clientId());
                if (basicAuthCredentials.isPresent()) {
                  // External Authorization with BasicAuth
                  log.debug("----------------BASIC AUTH HEADER-------------");
                  debugRequestScenario = "basic-auth";

                  String encodedBasicAuth =
                      BasicAuthUtil.encodeBasicAuth(
                          basicAuthCredentials.get().getUsername(),
                          basicAuthCredentials.get().getPassword());

                  HeaderUtil.addHeader(
                      requestMutationBuilder,
                      Constants.HEADER_AUTHORIZATION,
                      Constants.BASIC + " " + encodedBasicAuth);

                } else {

                  if (Objects.nonNull(
                      effectiveRequestConfigResolver.resolveExternalTokenEndpoint(
                          jumperConfig, headerConfig))) {

                    ExchangeStateManager.setOAuthFilterRequired(exchange, true);

                  } else {
                    // Enhanced Last Mile Security Token scenario
                    log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                    debugRequestScenario = "last-mile-security";

                    String providerLmsToken =
                        tokenGeneratorService.generateProviderLmsToken(
                            incomingTokenClaims,
                            effectiveRequestConfigResolver.resolveLmsSecurityScopes(
                                jumperConfig, incomingTokenClaims.clientId()),
                            consumerRequestPath,
                            environment,
                            String.valueOf(incomingRequest.getMethod()),
                            localIssuerBaseUrl + "/" + realmName,
                            HeaderUtil.getLastValueFromHeaderField(
                                incomingRequest, Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                            HeaderUtil.getLastValueFromHeaderField(
                                incomingRequest, Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID));

                    HeaderUtil.addHeader(
                        requestMutationBuilder,
                        Constants.HEADER_AUTHORIZATION,
                        Constants.BEARER + " " + providerLmsToken);
                    log.debug("lastMileSecurityToken: " + providerLmsToken);
                  }
                }
              }
            }
          }

          HeaderUtil.addHeader(
              requestMutationBuilder,
              Constants.HEADER_X_ORIGIN_STARGATE,
              incomingTokenClaims.originGateway());
          HeaderUtil.addHeader(
              requestMutationBuilder,
              Constants.HEADER_X_ORIGIN_ZONE,
              incomingTokenClaims.originZone());
          HeaderUtil.rewriteXForwardedHeader(
              requestMutationBuilder, incomingTokenClaims.originGateway());

          if (log.isDebugEnabled()) {
            log.atDebug()
                .setMessage("Logging request")
                .addKeyValue("scenario", debugRequestScenario)
                .addKeyValue("method", incomingRequest.getMethod())
                .addKeyValue("consumer", incomingTokenClaims.clientId())
                .addKeyValue("basePath", apiBasePath)
                .addKeyValue("requestPath", consumerRequestPath)
                .addKeyValue("upstreamUri", finalUpstreamUri)
                .log();
          }

          HeaderUtil.removeHeaders(requestMutationBuilder, jumperConfig.getRemoveHeaders());
          tracer.currentSpan().event("jrqf");

          // store final destination url to exchange
          log.debug("Routing set to: " + finalUpstreamUri);
          requestMutationBuilder.uri(finalUpstreamUri);
          ServerHttpRequest finalRequest = requestMutationBuilder.build();
          exchange
              .getAttributes()
              .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, finalUpstreamUri);

          ServerWebExchange finalExchange = exchange.mutate().request(finalRequest).build();
          log.debug("final RequestFilter uri: {}", finalExchange.getRequest().getURI());
          var gatewayRequestUrl =
              finalExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
          log.debug(
              "final exchange attribute GatewayRequestUrlAttr: {}",
              gatewayRequestUrl != null ? gatewayRequestUrl.toString() : "null");
          return chain.filter(finalExchange);
        },
        RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
  }

  private URI buildFinalUpstreamUri(URI requestUri, String forwardedPath, String upstreamBaseUrl) {

    try {
      if (Objects.nonNull(requestUri.getRawQuery())) {
        forwardedPath += "?" + requestUri.getRawQuery();
      }

      if (Objects.nonNull(requestUri.getFragment())) {
        forwardedPath += "#" + requestUri.getFragment();
      }

      String normalizedUpstreamBaseUrl =
          upstreamBaseUrl.endsWith("/")
              ? upstreamBaseUrl.substring(0, upstreamBaseUrl.length() - 1)
              : upstreamBaseUrl;
      String finalUpstreamUrl = normalizedUpstreamBaseUrl + forwardedPath;

      return new URI(finalUpstreamUrl);

    } catch (URISyntaxException e) {
      throw new RuntimeException("can not construct URL from " + requestUri, e);
    }
  }

  /**
   * Whether this route is a cross-zone mesh route and should generate a mesh LMS token instead of a
   * provider LMS token.
   *
   * <p>{@code mesh} is the canonical signal set by the control plane in the {@code jumper_config}
   * or {@code routing_config} object. The resolved internal token endpoint is a transitional
   * fallback for pre-migration proxy routes that still carry {@code issuer} but no {@code mesh}.
   *
   * <p>TODO: drop the internal-token-endpoint fallback once the control-plane migration for Mesh
   * LMS phase 2 is complete.
   */
  private boolean isMeshRoute(JumperConfig config, HeaderConfig headers) {
    return Boolean.TRUE.equals(config.getMesh())
        || Objects.nonNull(
            effectiveRequestConfigResolver.resolveInternalTokenEndpoint(config, headers));
  }

  private static String stripJumperPathPrefix(ServerHttpRequest request, String jumperPathPrefix) {
    String rawPath = request.getURI().getRawPath();
    return rawPath.startsWith(jumperPathPrefix)
        ? rawPath.substring(jumperPathPrefix.length())
        : rawPath;
  }

  private static RouteListener findListenerForConsumer(JumperConfig config, String clientId) {
    return Objects.isNull(config.getRouteListener())
        ? null
        : config.getRouteListener().get(clientId);
  }

  private JumperConfig resolveListenerConfig(
      ServerHttpRequest request, HeaderConfig headers, JumperConfig selectedConfig) {
    // Listener configuration remains on the top-level jumper_config when a routing_config selects
    // the upstream target.
    return headers.hasRoutingConfigHeader()
        ? requestHeaderParser.readJumperConfig(request)
        : selectedConfig;
  }

  private static Optional<BasicAuthCredentials> resolveBasicAuthCredentials(
      JumperConfig config, String clientId) {
    if (Objects.isNull(config.getBasicAuth())) {
      return Optional.empty();
    }
    if (config.getBasicAuth().containsKey(clientId)) {
      return Optional.of(config.getBasicAuth().get(clientId));
    }
    return Optional.ofNullable(config.getBasicAuth().get(Constants.BASIC_AUTH_PROVIDER_KEY));
  }

  private static void setXSpaceGateTokenHeader(ServerHttpRequest.Builder builder, String token) {
    HeaderUtil.addHeader(builder, Constants.HEADER_X_SPACEGATE_TOKEN, token);
  }

  private void forwardXTokenExchangeAsAuthorization(
      ServerHttpRequest.Builder builder, ServerHttpRequest request) {

    HeaderUtil.addHeader(
        builder,
        Constants.HEADER_AUTHORIZATION,
        HeaderUtil.getFirstValueFromHeaderField(request, Constants.HEADER_X_TOKEN_EXCHANGE));

    log.debug(
        "x-token-exchange: "
            + HeaderUtil.getFirstValueFromHeaderField(request, Constants.HEADER_X_TOKEN_EXCHANGE));
  }

  private boolean isInternetFacingZone(String zone) {

    return zone != null && internetFacingZones.contains(zone);
  }

  private void enrichCurrentSpanFromRequest(ServerHttpRequest request) {
    Span span = this.tracer.currentSpan();

    String xTardisTraceId =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_TARDIS_TRACE_ID);
    if (xTardisTraceId != null) {
      span.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
    }

    String contentLength = HeaderUtil.getLastValueFromHeaderField(request, "Content-Length");
    span.tag("message.size", Objects.requireNonNullElse(contentLength, "0"));
  }

  @AllArgsConstructor
  @Getter
  public static class Config extends AbstractGatewayFilterFactory.NameConfig {
    // Jumper-owned route prefix removed before forwarding, either /proxy or /listener.
    private String jumperPathPrefix;
  }
}

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
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.service.*;
import jumper.util.BasicAuthUtil;
import jumper.util.ExchangeStateManager;
import jumper.util.HeaderUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
  private final JumperConfigService jumperConfigService;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${jumper.zone.name}")
  private String currentZone;

  @Value("#{'${jumper.zone.internetFacingZones}'.toLowerCase().split(',')}")
  private List<String> internetFacingZones;

  public static final int REQUEST_FILTER_ORDER =
      RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

  public RequestFilter(
      Tracer tracer,
      TokenGeneratorService tokenGeneratorService,
      JumperConfigService jumperConfigService) {
    super(Config.class);
    this.tracer = tracer;
    this.tokenGeneratorService = tokenGeneratorService;
    this.jumperConfigService = jumperConfigService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest readOnlyRequest = exchange.getRequest();
          addOriginalRequestUrl(exchange, readOnlyRequest.getURI());

          ServerHttpRequest.Builder requestMutationBuilder = readOnlyRequest.mutate();

          enrichTracingWithDataFrom(readOnlyRequest);

          JumperConfig jumperConfig = jumperConfigService.resolveJumperConfig(readOnlyRequest);

          // calculate routing stuff and add it to exchange and JumperConfig
          URI finalApiUri =
              calculateFinalApiUri(readOnlyRequest, config.getRoutePathPrefix(), jumperConfig);

          // ListenerRoute was called, jumperConfig is stored in exchange for usage with Spectre
          if (config.getRoutePathPrefix().equals(Constants.LISTENER_ROOT_PATH_PREFIX)) {
            ExchangeStateManager.setJumperConfig(exchange, jumperConfig);
          }

          if (jumperConfig.getSecondaryFailover()) {
            // write audit log if needed
            AuditLogService.writeFailoverAuditLog(jumperConfig);

            // pass headers from config to provider
            HeaderUtil.addHeader(
                requestMutationBuilder, Constants.HEADER_REALM, jumperConfig.getRealmName());
            HeaderUtil.addHeader(
                requestMutationBuilder, Constants.HEADER_ENVIRONMENT, jumperConfig.getEnvName());
          }

          // handle request
          Optional<JumperInfoRequest> jumperInfoRequest = initializeJumperInfoRequest();

          if (!jumperConfig.getRemoteApiUrl().startsWith(Constants.LOCALHOST_ISSUER_SERVICE)) {

            if (Objects.nonNull(jumperConfig.getInternalTokenEndpoint())) {
              // GW-2-GW MESH TOKEN GENERATION
              log.debug("----------------GATEWAY MESH-------------");
              jumperInfoRequest.ifPresent(
                  i -> i.setInfoScenario(false, false, true, false, false, false));

              HeaderUtil.addHeader(
                  requestMutationBuilder,
                  Constants.HEADER_CONSUMER_TOKEN,
                  jumperConfig.getConsumerToken());

              checkForInternetFacingZone(
                  requestMutationBuilder,
                  jumperConfig.getConsumerOriginZone(),
                  jumperConfig.getConsumerToken());

              ExchangeStateManager.setOAuthFilterRequired(exchange, true);

            } else {
              // ALL NON MESH SCENARIOS

              if (readOnlyRequest.getHeaders().containsKey(Constants.HEADER_X_TOKEN_EXCHANGE)
                  && isInternetFacingZone(currentZone)) {

                log.debug("----------------X-TOKEN-EXCHANGE HEADER-------------");
                jumperInfoRequest.ifPresent(
                    i -> i.setInfoScenario(false, false, false, false, false, true));

                addXtokenExchange(requestMutationBuilder, readOnlyRequest);

              } else {
                Optional<BasicAuthCredentials> basicAuthCredentials =
                    jumperConfig.getBasicAuthCredentials();
                if (basicAuthCredentials.isPresent()) {
                  // External Authorization with BasicAuth
                  log.debug("----------------BASIC AUTH HEADER-------------");
                  jumperInfoRequest.ifPresent(
                      i -> i.setInfoScenario(false, false, false, false, true, false));

                  String encodedBasicAuth =
                      BasicAuthUtil.encodeBasicAuth(
                          basicAuthCredentials.get().getUsername(),
                          basicAuthCredentials.get().getPassword());

                  HeaderUtil.addHeader(
                      requestMutationBuilder,
                      Constants.HEADER_AUTHORIZATION,
                      Constants.BASIC + " " + encodedBasicAuth);

                } else {

                  if (Objects.nonNull(jumperConfig.getExternalTokenEndpoint())) {

                    ExchangeStateManager.setOAuthFilterRequired(exchange, true);

                  } else {
                    // Enhanced Last Mile Security Token scenario
                    log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                    jumperInfoRequest.ifPresent(
                        i -> i.setInfoScenario(true, true, false, false, false, false));

                    String enhancedLastmileSecurityToken =
                        tokenGeneratorService.generateEnhancedLastMileGatewayToken(
                            jumperConfig,
                            String.valueOf(readOnlyRequest.getMethod()),
                            localIssuerUrl + "/" + jumperConfig.getRealmName(),
                            HeaderUtil.getLastValueFromHeaderField(
                                readOnlyRequest, Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                            HeaderUtil.getLastValueFromHeaderField(
                                readOnlyRequest, Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID),
                            false);

                    HeaderUtil.addHeader(
                        requestMutationBuilder,
                        Constants.HEADER_AUTHORIZATION,
                        Constants.BEARER + " " + enhancedLastmileSecurityToken);
                    log.debug("lastMileSecurityToken: " + enhancedLastmileSecurityToken);
                  }
                }
              }
            }
          }

          HeaderUtil.addHeader(
              requestMutationBuilder,
              Constants.HEADER_X_ORIGIN_STARGATE,
              jumperConfig.getConsumerOriginStargate());
          HeaderUtil.addHeader(
              requestMutationBuilder,
              Constants.HEADER_X_ORIGIN_ZONE,
              jumperConfig.getConsumerOriginZone());
          HeaderUtil.rewriteXForwardedHeader(requestMutationBuilder, jumperConfig);

          jumperInfoRequest.ifPresent(
              infoRequest -> {
                IncomingRequest incReq = createIncomingRequest(jumperConfig, readOnlyRequest);
                infoRequest.setIncomingRequest(incReq);
                log.atInfo()
                    .setMessage("logging request:")
                    .addKeyValue("jumperInfo", infoRequest)
                    .log();
              });

          HeaderUtil.removeHeaders(requestMutationBuilder, jumperConfig.getRemoveHeaders());
          tracer.currentSpan().event("jrqf");

          // store final destination url to exchange
          log.debug("Routing set to: " + finalApiUri);
          requestMutationBuilder.uri(finalApiUri);
          ServerHttpRequest finalRequest = requestMutationBuilder.build();
          exchange
              .getAttributes()
              .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, finalApiUri);

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

  private Optional<JumperInfoRequest> initializeJumperInfoRequest() {

    if (log.isDebugEnabled()) {
      JumperInfoRequest jumperInfoRequest = new JumperInfoRequest();
      return Optional.of(jumperInfoRequest);
    }

    return Optional.empty();
  }

  private IncomingRequest createIncomingRequest(
      JumperConfig jumperConfig, ServerHttpRequest request) {
    IncomingRequest incReq = new IncomingRequest();
    incReq.setConsumer(jumperConfig.getConsumer());
    incReq.setBasePath(jumperConfig.getApiBasePath());
    incReq.setFinalApiUrl(jumperConfig.getFinalApiUrl());
    incReq.setMethod(request.getMethod().name());
    incReq.setRequestPath(jumperConfig.getRequestPath());

    return incReq;
  }

  private URI calculateFinalApiUri(
      ServerHttpRequest request, String routePathPrefix, JumperConfig jumperConfig) {

    try {
      URI uri = request.getURI();

      String rawPath = uri.getRawPath();
      String routingPath =
          rawPath.startsWith(routePathPrefix)
              ? rawPath.substring(routePathPrefix.length())
              : rawPath;

      String requestPath = jumperConfig.getApiBasePath() + routingPath;

      if (Objects.nonNull(uri.getRawQuery())) {
        routingPath += "?" + uri.getRawQuery();
      }

      if (Objects.nonNull(uri.getFragment())) {
        routingPath += "#" + uri.getFragment();
      }

      String normalizedRemoteApiUrl =
          jumperConfig.getRemoteApiUrl().endsWith("/")
              ? jumperConfig
                  .getRemoteApiUrl()
                  .substring(0, jumperConfig.getRemoteApiUrl().length() - 1)
              : jumperConfig.getRemoteApiUrl();
      String finalApiUrl = normalizedRemoteApiUrl + routingPath;

      // add calculated stuff to jumperConfig
      jumperConfig.setRequestPath(requestPath);
      jumperConfig.setRoutingPath(routingPath);
      jumperConfig.setFinalApiUrl(finalApiUrl);

      return new URI(finalApiUrl);

    } catch (URISyntaxException e) {
      throw new RuntimeException("can not construct URL from " + request.getURI(), e);
    }
  }

  private void checkForInternetFacingZone(
      ServerHttpRequest.Builder builder, String zone, String token) {
    if (isInternetFacingZone(zone)) {
      HeaderUtil.addHeader(builder, Constants.HEADER_X_SPACEGATE_TOKEN, token);
    }
  }

  private void addXtokenExchange(ServerHttpRequest.Builder builder, ServerHttpRequest request) {

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

  private void enrichTracingWithDataFrom(ServerHttpRequest request) {
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
    private String routePathPrefix;
  }
}

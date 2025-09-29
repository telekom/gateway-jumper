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
import jumper.filter.strategy.AuthenticationStrategy;
import jumper.model.config.JumperConfig;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.service.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@Component
@Slf4j
@Setter
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

  private final Tracer tracer;
  private final ZoneHealthCheckService zoneHealthCheckService;
  private final List<AuthenticationStrategy> authenticationStrategies;

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
      ZoneHealthCheckService zoneHealthCheckService,
      List<AuthenticationStrategy> authenticationStrategies) {
    super(Config.class);
    this.tracer = tracer;
    this.zoneHealthCheckService = zoneHealthCheckService;
    this.authenticationStrategies = authenticationStrategies;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          var context = createRequestContext(exchange, config);

          handleListenerRoute(context);
          handleFailoverAuditLog(context);
          applyAuthentication(context);
          addStandardHeaders(context);
          logRequest(context);
          finalizeRequest(context);

          return chain.filter(context.getFinalExchange());
        },
        REQUEST_FILTER_ORDER);
  }

  private RequestProcessingContext createRequestContext(ServerWebExchange exchange, Config config) {
    ServerHttpRequest readOnlyRequest = exchange.getRequest();
    addOriginalRequestUrl(exchange, readOnlyRequest.getURI());

    ServerHttpRequest.Builder requestMutationBuilder = readOnlyRequest.mutate();
    enrichTracingWithDataFrom(readOnlyRequest);

    JumperConfig jumperConfig = extractJumperConfig(readOnlyRequest);
    URI finalApiUri =
        calculateFinalApiUri(readOnlyRequest, config.getRoutePathPrefix(), jumperConfig);
    Optional<JumperInfoRequest> jumperInfoRequest = initializeJumperInfoRequest();

    return new RequestProcessingContext(
        exchange,
        config,
        readOnlyRequest,
        requestMutationBuilder,
        jumperConfig,
        jumperInfoRequest,
        finalApiUri,
        currentZone,
        internetFacingZones,
        localIssuerUrl);
  }

  private void handleListenerRoute(RequestProcessingContext context) {
    if (context.getConfig().getRoutePathPrefix().equals(Constants.LISTENER_ROOT_PATH_PREFIX)) {
      context
          .getExchange()
          .getAttributes()
          .put(
              Constants.HEADER_JUMPER_CONFIG, JumperConfig.toJsonBase64(context.getJumperConfig()));
    }
  }

  private void handleFailoverAuditLog(RequestProcessingContext context) {
    if (context.getJumperConfig().getSecondaryFailover()) {
      AuditLogService.writeFailoverAuditLog(context.getJumperConfig());

      HeaderUtil.addHeader(
          context.getRequestBuilder(),
          Constants.HEADER_REALM,
          context.getJumperConfig().getRealmName());
      HeaderUtil.addHeader(
          context.getRequestBuilder(),
          Constants.HEADER_ENVIRONMENT,
          context.getJumperConfig().getEnvName());
    }
  }

  private void applyAuthentication(RequestProcessingContext context) {
    authenticationStrategies.stream()
        .filter(strategy -> strategy.canHandle(context))
        .findFirst()
        .ifPresent(
            strategy -> {
              log.debug("Applying authentication strategy: {}", strategy.getStrategyName());
              strategy.authenticate(context);
            });
  }

  private void addStandardHeaders(RequestProcessingContext context) {
    HeaderUtil.addHeader(
        context.getRequestBuilder(),
        Constants.HEADER_X_ORIGIN_STARGATE,
        context.getJumperConfig().getConsumerOriginStargate());
    HeaderUtil.addHeader(
        context.getRequestBuilder(),
        Constants.HEADER_X_ORIGIN_ZONE,
        context.getJumperConfig().getConsumerOriginZone());
    HeaderUtil.rewriteXForwardedHeader(context.getRequestBuilder(), context.getJumperConfig());
  }

  private void logRequest(RequestProcessingContext context) {
    context
        .getJumperInfoRequest()
        .ifPresent(
            infoRequest -> {
              IncomingRequest incReq =
                  createIncomingRequest(context.getJumperConfig(), context.getReadOnlyRequest());
              infoRequest.setIncomingRequest(incReq);
              log.atInfo()
                  .setMessage("logging request:")
                  .addKeyValue("jumperInfo", infoRequest)
                  .log();
            });
  }

  private void finalizeRequest(RequestProcessingContext context) {
    HeaderUtil.removeHeaders(
        context.getRequestBuilder(), context.getJumperConfig().getRemoveHeaders());
    tracer.currentSpan().event("jrqf");

    log.debug("Routing set to: " + context.getFinalApiUri());
    context.getRequestBuilder().uri(context.getFinalApiUri());
    ServerHttpRequest finalRequest = context.getRequestBuilder().build();
    context
        .getExchange()
        .getAttributes()
        .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, context.getFinalApiUri());

    ServerWebExchange finalExchange = context.getExchange().mutate().request(finalRequest).build();
    context.setFinalExchange(finalExchange);

    log.debug("final RequestFilter uri: {}", finalExchange.getRequest().getURI());
    log.debug(
        "final exchange attribute GatewayRequestUrlAttr: {}",
        finalExchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR).toString());
  }

  private void setOAuthFilterNeeded(ServerWebExchange exchange, boolean isNeeded) {
    exchange.getAttributes().put(Constants.GATEWAY_ATTRIBUTE_OAUTH_FILTER_NEEDED, isNeeded);
  }

  private JumperConfig extractJumperConfig(ServerHttpRequest readOnlyRequest) {
    JumperConfig jumperConfig;
    // failover logic if routing_config header present
    if (readOnlyRequest.getHeaders().containsKey(Constants.HEADER_ROUTING_CONFIG)) {
      // evaluate routingConfig for failover scenario
      List<JumperConfig> jumperConfigList = JumperConfig.parseJumperConfigListFrom(readOnlyRequest);
      log.debug("failover case, routing_config: {}", jumperConfigList);
      jumperConfig =
          evaluateTargetZone(
              jumperConfigList,
              readOnlyRequest.getHeaders().getFirst(Constants.HEADER_X_FAILOVER_SKIP_ZONE));
      jumperConfig.fillProcessingInfo(readOnlyRequest);
      log.debug("failover case, enhanced jumper_config: {}", jumperConfig);

    }

    // no failover
    else {
      // Prepare and extract JumperConfigValues
      jumperConfig = JumperConfig.parseAndFillJumperConfigFrom(readOnlyRequest);
      log.debug("JumperConfig decoded: {}", jumperConfig);
    }
    return jumperConfig;
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

      String finalApiUrl =
          (jumperConfig.getRemoteApiUrl().endsWith("/")
                  ? jumperConfig
                      .getRemoteApiUrl()
                      .substring(0, jumperConfig.getRemoteApiUrl().length() - 1)
                  : jumperConfig.getRemoteApiUrl())
              + routingPath;

      // add calculated stuff to jumperConfig
      jumperConfig.setRequestPath(requestPath);
      jumperConfig.setRoutingPath(routingPath);
      jumperConfig.setFinalApiUrl(finalApiUrl);

      return new URI(finalApiUrl);

    } catch (URISyntaxException e) {
      throw new RuntimeException("can not construct URL from " + request.getURI(), e);
    }
  }

  private JumperConfig evaluateTargetZone(
      List<JumperConfig> jumperConfigList, String forceSkipZone) {
    for (JumperConfig jc : jumperConfigList) {
      // secondary route, failover in place => audit logs
      if (StringUtils.isEmpty(jc.getTargetZoneName())) {
        jc.setSecondaryFailover(true);
        return jc;
      }
      // targetZoneName present, check it against force skip header and zones state
      // map
      if (!(jc.getTargetZoneName().equalsIgnoreCase(forceSkipZone)
          || !zoneHealthCheckService.getZoneHealth(jc.getTargetZoneName()))) {
        return jc;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Non of defined failover zones available");
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

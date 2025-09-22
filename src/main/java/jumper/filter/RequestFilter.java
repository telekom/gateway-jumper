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
import jumper.model.TokenInfo;
import jumper.model.config.BasicAuthCredentials;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.service.*;
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
  private final OauthTokenUtil oauthTokenUtil;
  private final BasicAuthUtil basicAuthUtil;
  private final ZoneHealthCheckService zoneHealthCheckService;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${jumper.zone.name}")
  private String currentZone;

  @Value("#{'${jumper.zone.internetFacingZones}'.toLowerCase().split(',')}")
  private List<String> internetFacingZones;

  @Value("${spring.application.name}")
  private String applicationName;

  public static final int REQUEST_FILTER_ORDER =
      RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

  public RequestFilter(
      Tracer tracer,
      OauthTokenUtil oauthTokenUtil,
      BasicAuthUtil basicAuthUtil,
      ZoneHealthCheckService zoneHealthCheckService) {
    super(Config.class);
    this.tracer = tracer;
    this.oauthTokenUtil = oauthTokenUtil;
    this.basicAuthUtil = basicAuthUtil;
    this.zoneHealthCheckService = zoneHealthCheckService;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          ServerHttpRequest readOnlyRequest = exchange.getRequest();
          addOriginalRequestUrl(exchange, readOnlyRequest.getURI());

          ServerHttpRequest.Builder requestMutationBuilder = readOnlyRequest.mutate();

          enrichTracingWithDataFrom(readOnlyRequest);

          JumperConfig jumperConfig;
          // failover logic if routing_config header present
          if (readOnlyRequest.getHeaders().containsKey(Constants.HEADER_ROUTING_CONFIG)) {
            // evaluate routingConfig for failover scenario
            List<JumperConfig> jumperConfigList =
                JumperConfig.parseJumperConfigListFrom(readOnlyRequest);
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

          // calculate routing stuff and add it to exchange and JumperConfig
          URI finalApiUri =
              calculateFinalApiUri(readOnlyRequest, config.getRoutePathPrefix(), jumperConfig);

          if (config.getRoutePathPrefix().equals(Constants.LISTENER_ROOT_PATH_PREFIX)) {
            // ListenerRoute was called, jumperConfig is stored in exchange for later
            // usage
            // within Spectre
            exchange
                .getAttributes()
                .put(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toJsonBase64(jumperConfig));
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

              TokenInfo meshTokenInfo = oauthTokenUtil.getInternalMeshAccessToken(jumperConfig);

              // set gw and consumer tokens correctly
              HeaderUtil.addHeader(
                  requestMutationBuilder,
                  Constants.HEADER_AUTHORIZATION,
                  "Bearer " + meshTokenInfo.getAccessToken());
              HeaderUtil.addHeader(
                  requestMutationBuilder,
                  Constants.HEADER_CONSUMER_TOKEN,
                  jumperConfig.getConsumerToken());

              checkForInternetFacingZone(
                  requestMutationBuilder,
                  jumperConfig.getConsumerOriginZone(),
                  jumperConfig.getConsumerToken());

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
                      basicAuthUtil.encodeBasicAuth(
                          basicAuthCredentials.get().getUsername(),
                          basicAuthCredentials.get().getPassword());

                  HeaderUtil.addHeader(
                      requestMutationBuilder,
                      Constants.HEADER_AUTHORIZATION,
                      Constants.BASIC + " " + encodedBasicAuth);

                } else {

                  if (Objects.nonNull(jumperConfig.getExternalTokenEndpoint())) {
                    // External Authorization with OAuth
                    log.debug("----------------EXTERNAL AUTHORIZATION-------------");
                    log.debug(
                        "Remote TokenEndpoint is set to: {}",
                        jumperConfig.getExternalTokenEndpoint());
                    jumperInfoRequest.ifPresent(
                        i -> i.setInfoScenario(false, false, false, true, false, false));

                    Optional<OauthCredentials> oauthCredentials =
                        jumperConfig.getOauthCredentials();
                    if (oauthCredentials.isPresent()
                        && StringUtils.isNotBlank(oauthCredentials.get().getGrantType())) {

                      TokenInfo tokenInfo =
                          oauthTokenUtil.getAccessTokenWithOauthCredentialsObject(
                              jumperConfig.getExternalTokenEndpoint(), oauthCredentials.get());

                      HeaderUtil.addHeader(
                          requestMutationBuilder,
                          Constants.HEADER_AUTHORIZATION,
                          Constants.BEARER + " " + tokenInfo.getAccessToken());

                    } else {
                      getAccessTokenFromExternalIdpLegacy(requestMutationBuilder, jumperConfig);
                    }

                  } else if (Boolean.FALSE.equals(jumperConfig.getAccessTokenForwarding())) {
                    // Enhanced Last Mile Security Token scenario
                    log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                    jumperInfoRequest.ifPresent(
                        i -> i.setInfoScenario(true, true, false, false, false, false));

                    String enhancedLastmileSecurityToken =
                        oauthTokenUtil.generateEnhancedLastMileGatewayToken(
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

                  } else {
                    // (Legacy) Last Mile Security Token scenario
                    log.debug("----------------LAST MILE SECURITY (LEGACY)-------------");
                    jumperInfoRequest.ifPresent(
                        i -> i.setInfoScenario(true, false, false, false, false, false));

                    String legacyLastmileSecurityToken =
                        oauthTokenUtil.generateEnhancedLastMileGatewayToken(
                            jumperConfig,
                            String.valueOf(readOnlyRequest.getMethod()),
                            localIssuerUrl + "/" + jumperConfig.getRealmName(),
                            HeaderUtil.getLastValueFromHeaderField(
                                readOnlyRequest, Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                            HeaderUtil.getLastValueFromHeaderField(
                                readOnlyRequest, Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID),
                            true);

                    HeaderUtil.addHeader(
                        requestMutationBuilder,
                        Constants.HEADER_LASTMILE_SECURITY_TOKEN,
                        Constants.BEARER + " " + legacyLastmileSecurityToken);
                    log.debug("lastMileSecurityToken: " + legacyLastmileSecurityToken);
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
          log.debug(
              "final exchange attribute GatewayRequestUrlAttr: {}",
              finalExchange
                  .getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)
                  .toString());
          return chain.filter(finalExchange);
        },
        RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
  }

  private Optional<JumperInfoRequest> initializeJumperInfoRequest() {

    if (log.isInfoEnabled()) {
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

  private void getAccessTokenFromExternalIdpLegacy(
      ServerHttpRequest.Builder builder, JumperConfig jc) {

    String consumer = jc.getConsumer();
    String tokenEndpoint = jc.getExternalTokenEndpoint();

    Optional<OauthCredentials> oauthCredentials = jc.getOauthCredentials();

    String clientId = determineClientId(builder, jc, oauthCredentials);
    String clientSecret = determineClientSecret(builder, jc, oauthCredentials);
    String clientScope = determineClientScope(builder, jc, oauthCredentials);

    log.debug("Get token for consumer: {} with clientId: {}", consumer, clientId);
    if (Objects.nonNull(clientId) && Objects.nonNull(clientSecret)) {
      TokenInfo tokenInfo =
          oauthTokenUtil.getAccessTokenWithClientCredentials(
              tokenEndpoint, clientId, clientSecret, clientScope);
      HeaderUtil.addHeader(
          builder,
          Constants.HEADER_AUTHORIZATION,
          Constants.BEARER + " " + tokenInfo.getAccessToken());

    } else {
      log.warn("not specified oauth config credentials for consumer: {}", consumer);
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Missing oauth config credentials for consumer " + consumer);
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

package jumper.filter;

import static net.logstash.logback.argument.StructuredArguments.value;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import jumper.Constants;
import jumper.model.TokenInfo;
import jumper.model.config.BasicAuthCredentials;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.model.request.IncomingRequest;
import jumper.model.request.JumperInfoRequest;
import jumper.service.BasicAuthUtil;
import jumper.service.HeaderUtil;
import jumper.service.OauthTokenUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

@Component
@Slf4j
public class RequestFilter extends AbstractGatewayFilterFactory<RequestFilter.Config> {

  private final CurrentTraceContext currentTraceContext;
  private final Tracer tracer;
  private final OauthTokenUtil oauthTokenUtil;
  private final BasicAuthUtil basicAuthUtil;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${spring.application.name}")
  private String applicationName;

  public static final int REQUEST_FILTER_ORDER =
      RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1;

  public RequestFilter(
      CurrentTraceContext currentTraceContext,
      Tracer tracer,
      OauthTokenUtil oauthTokenUtil,
      BasicAuthUtil basicAuthUtil) {
    super(Config.class);
    this.currentTraceContext = currentTraceContext;
    this.tracer = tracer;
    this.oauthTokenUtil = oauthTokenUtil;
    this.basicAuthUtil = basicAuthUtil;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return new OrderedGatewayFilter(
        (exchange, chain) -> {
          WebFluxSleuthOperators.withSpanInScope(
              tracer,
              currentTraceContext,
              exchange,
              () -> {
                ServerHttpRequest request = exchange.getRequest();

                // checking to prevent later nullPointer on inconsistent state from Kong
                if (!request.getHeaders().containsKey(Constants.HEADER_REMOTE_API_URL)) {
                  throw new RuntimeException(
                      "missing mandatory header " + Constants.HEADER_REMOTE_API_URL);
                }

                // Prepare and extract JumperConfigValues
                JumperConfig jumperConfig = JumperConfig.parseConfigFrom(request);
                log.debug("JumperConfig encodedAsBase64: {}", JumperConfig.toBase64(jumperConfig));
                log.debug("JumperConfig decoded: {}", jumperConfig);

                // calculate routing stuff and add it to exchange and JumperConfig
                calculateRoutingStuff(request, exchange, config.getRoutePathPrefix(), jumperConfig);

                if (config.getRoutePathPrefix().equals(Constants.LISTENER_ROOT_PATH_PREFIX)) {
                  // ListenerRoute was called, jumperConfig is stored in exchange for later usage
                  // within Spectre
                  exchange
                      .getAttributes()
                      .put(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toBase64(jumperConfig));
                }

                // handle request
                Optional<JumperInfoRequest> jumperInfoRequest =
                    initializeJumperInfoRequest(jumperConfig);

                if (!jumperConfig
                    .getRemoteApiUrl()
                    .startsWith(Constants.LOCALHOST_ISSUER_SERVICE)) {

                  if (Objects.nonNull(jumperConfig.getInternalTokenEndpoint())) {
                    // GW-2-GW MESH TOKEN GENERATION
                    log.debug("----------------GATEWAY MESH-------------");
                    jumperInfoRequest.ifPresent(
                        i -> i.setInfoScenario(false, false, true, false, false));

                    TokenInfo meshTokenInfo =
                        oauthTokenUtil.getInternalMeshAccessToken(jumperConfig);

                    // set gw and consumer tokens correctly
                    HeaderUtil.addHeader(
                        exchange,
                        Constants.HEADER_AUTHORIZATION,
                        "Bearer " + meshTokenInfo.getAccessToken());
                    HeaderUtil.addHeader(
                        exchange, Constants.HEADER_CONSUMER_TOKEN, jumperConfig.getConsumerToken());

                    checkForSpaceZone(
                        exchange,
                        jumperConfig.getConsumerOriginZone(),
                        jumperConfig.getConsumerToken());

                  } else {
                    // ALL NON MESH SCENARIOS

                    Optional<BasicAuthCredentials> basicAuthCredentials =
                        jumperConfig.getBasicAuthCredentials();
                    if (basicAuthCredentials.isPresent()) {
                      // External Authorization with BasicAuth
                      log.debug("----------------BASIC AUTH HEADER-------------");
                      jumperInfoRequest.ifPresent(
                          i -> i.setInfoScenario(false, false, false, false, true));

                      String encodedBasicAuth =
                          basicAuthUtil.encodeBasicAuth(
                              basicAuthCredentials.get().getUsername(),
                              basicAuthCredentials.get().getPassword());

                      HeaderUtil.addHeader(
                          exchange,
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
                            i -> i.setInfoScenario(false, false, false, true, false));

                        Optional<OauthCredentials> oauthCredentials =
                            jumperConfig.getOauthCredentials();
                        if (oauthCredentials.isPresent()
                            && StringUtils.isNotBlank(oauthCredentials.get().getGrantType())) {

                          TokenInfo tokenInfo =
                              oauthTokenUtil.getAccessTokenWithOauthCredentialsObject(
                                  jumperConfig.getExternalTokenEndpoint(),
                                  oauthCredentials.get(),
                                  jumperConfig.getConsumer());

                          HeaderUtil.addHeader(
                              exchange,
                              Constants.HEADER_AUTHORIZATION,
                              Constants.BEARER + " " + tokenInfo.getAccessToken());

                        } else {
                          getAccessTokenFromExternalIdpLegacy(exchange, jumperConfig);
                        }

                      } else if (Boolean.FALSE.equals(jumperConfig.getAccessTokenForwarding())) {
                        // Enhanced Last Mile Security Token scenario
                        log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");
                        jumperInfoRequest.ifPresent(
                            i -> i.setInfoScenario(true, true, false, false, false));

                        String enhancedLastmileSecurityToken =
                            oauthTokenUtil.generateEnhancedLastMileGatewayToken(
                                jumperConfig,
                                String.valueOf(request.getMethod()),
                                localIssuerUrl + "/" + jumperConfig.getRealmName(),
                                HeaderUtil.getLastValueFromHeaderField(
                                    request, Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                                HeaderUtil.getLastValueFromHeaderField(
                                    request, Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID),
                                false);

                        HeaderUtil.addHeader(
                            exchange,
                            Constants.HEADER_AUTHORIZATION,
                            Constants.BEARER + " " + enhancedLastmileSecurityToken);
                        log.debug("lastMileSecurityToken: " + enhancedLastmileSecurityToken);

                      } else {
                        // (Legacy) Last Mile Security Token scenario
                        log.debug("----------------LAST MILE SECURITY (LEGACY)-------------");
                        jumperInfoRequest.ifPresent(
                            i -> i.setInfoScenario(true, false, false, false, false));

                        String legacyLastmileSecurityToken =
                            oauthTokenUtil.generateEnhancedLastMileGatewayToken(
                                jumperConfig,
                                String.valueOf(request.getMethod()),
                                localIssuerUrl + "/" + jumperConfig.getRealmName(),
                                HeaderUtil.getLastValueFromHeaderField(
                                    request, Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
                                HeaderUtil.getLastValueFromHeaderField(
                                    request, Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID),
                                true);

                        HeaderUtil.addHeader(
                            exchange,
                            Constants.HEADER_LASTMILE_SECURITY_TOKEN,
                            Constants.BEARER + " " + legacyLastmileSecurityToken);
                        log.debug("lastMileSecurityToken: " + legacyLastmileSecurityToken);
                      }
                    }
                  }
                }

                HeaderUtil.addHeader(
                    exchange,
                    Constants.HEADER_X_ORIGIN_STARGATE,
                    jumperConfig.getConsumerOriginStargate());
                HeaderUtil.addHeader(
                    exchange, Constants.HEADER_X_ORIGIN_ZONE, jumperConfig.getConsumerOriginZone());
                HeaderUtil.rewriteXForwardedHeader(exchange, jumperConfig);

                jumperInfoRequest.ifPresent(
                    infoRequest -> {
                      IncomingRequest incReq = createIncomingRequest(jumperConfig, request);
                      infoRequest.setIncomingRequest(incReq);
                      log.info("logging request: {}", value("jumperInfo", infoRequest));
                    });

                addTracingInfo(request);
              });

          return chain.filter(exchange);
        },
        RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER + 1);
  }

  private Optional<JumperInfoRequest> initializeJumperInfoRequest(JumperConfig jumperConfig) {

    if (log.isInfoEnabled()) {
      JumperInfoRequest jumperInfoRequest = new JumperInfoRequest();
      jumperInfoRequest.setEnvironment(jumperConfig.getEnvName());
      return Optional.of(jumperInfoRequest);
    }

    return Optional.empty();
  }

  private IncomingRequest createIncomingRequest(
      JumperConfig jumperConfig, ServerHttpRequest request) {
    IncomingRequest incReq = new IncomingRequest();
    incReq.setBasePath(jumperConfig.getApiBasePath());
    incReq.setHost(jumperConfig.getRemoteApiUrl());
    incReq.setMethod(String.valueOf(request.getMethod()));
    incReq.setResource(jumperConfig.getRoutingPath());

    HashMap<String, String> logEntries = new HashMap<>();
    logEntries.put("Thread name", Thread.currentThread().getName());

    incReq.setLogEntries(logEntries);
    return incReq;
  }

  private void calculateRoutingStuff(
      ServerHttpRequest request,
      ServerWebExchange exchange,
      String routePathPrefix,
      JumperConfig jumperConfig) {

    try {
      URI uri = request.getURI();
      String queryParameterPart = uri.getRawQuery();
      String fragmentPart = uri.getFragment();
      String routingPath = uri.getRawPath().replaceFirst("^" + routePathPrefix, "");

      String requestPath = jumperConfig.getApiBasePath() + routingPath;

      if (Objects.nonNull(queryParameterPart)) {
        routingPath += "?" + queryParameterPart;
      }

      if (Objects.nonNull(fragmentPart)) {
        routingPath += "#" + fragmentPart;
      }

      String finalApiUrl = jumperConfig.getRemoteApiUrl().replaceAll("/$", "") + routingPath;

      // store final destination url to exchange
      log.debug("Routing set to: " + finalApiUrl);
      exchange
          .getAttributes()
          .put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, new URI(finalApiUrl));

      // add calculated stuff to jumperConfig
      jumperConfig.setRequestPath(requestPath);
      jumperConfig.setRoutingPath(routingPath);

    } catch (URISyntaxException e) {
      throw new RuntimeException("can not construct URL from " + request.getURI(), e);
    }
  }

  private void getAccessTokenFromExternalIdpLegacy(ServerWebExchange exchange, JumperConfig jc) {

    String consumer = jc.getConsumer();
    String tokenEndpoint = jc.getExternalTokenEndpoint();

    Optional<OauthCredentials> oauthCredentials = jc.getOauthCredentials();

    String clientId = determineClientId(exchange, jc, oauthCredentials);
    String clientSecret = determineClientSecret(exchange, jc, oauthCredentials);
    String clientScope = determineClientScope(exchange, jc, oauthCredentials);

    log.debug("Get token for consumer: {} with clientId: {}", consumer, clientId);
    if (Objects.nonNull(clientId) && Objects.nonNull(clientSecret)) {
      TokenInfo tokenInfo =
          oauthTokenUtil.getAccessTokenWithClientCredentials(
              tokenEndpoint, clientId, clientSecret, clientScope, consumer);
      HeaderUtil.addHeader(
          exchange,
          Constants.HEADER_AUTHORIZATION,
          Constants.BEARER + " " + tokenInfo.getAccessToken());

    } else {
      log.warn("not specified oauth config credentials for consumer: {}", consumer);
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Missing oauth config credentials for consumer " + consumer);
    }
  }

  private static String determineClientScope(
      ServerWebExchange exchange, JumperConfig jc, Optional<OauthCredentials> oauthCredentials) {

    String clientScope = "";
    String xSpacegateScope = jc.getXSpacegateScope();

    if (Objects.nonNull(xSpacegateScope)) {
      log.debug("Using Scope from xSpacegateScope-Header");
      clientScope = xSpacegateScope;
      HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_SCOPE);

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
      ServerWebExchange exchange, JumperConfig jc, Optional<OauthCredentials> oauthCredentials) {

    String clientSecret = jc.getClientSecret();
    String xSpacegateClientSecret = jc.getXSpacegateClientSecret();

    if (Objects.nonNull(xSpacegateClientSecret)) {
      log.debug("Using SubscriberClientSecret from xSpacegateClientSecret-Header");
      clientSecret = xSpacegateClientSecret;
      HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET);

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
      ServerWebExchange exchange, JumperConfig jc, Optional<OauthCredentials> oauthCredentials) {

    String clientId = jc.getClientId();
    String xSpacegateClientId = jc.getXSpacegateClientId();

    if (StringUtils.isNotBlank(xSpacegateClientId)) {
      log.debug("Using SubscriberClientId {} from xSpacegateClientId-Header", xSpacegateClientId);
      clientId = xSpacegateClientId;
      HeaderUtil.removeHeader(exchange, Constants.HEADER_X_SPACEGATE_CLIENT_ID);

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

  private void checkForSpaceZone(ServerWebExchange exchange, String zone, String token) {
    if (zone != null && Constants.SPACE_ZONES.contains(zone)) {
      HeaderUtil.addHeader(exchange, Constants.HEADER_X_SPACEGATE_TOKEN, token);
    }
  }

  private void addTracingInfo(ServerHttpRequest request) {

    String xTardisTraceId =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_TARDIS_TRACE_ID);
    String contentLength = HeaderUtil.getLastValueFromHeaderField(request, "Content-Length");

    Span incomingRequestSpan = tracer.currentSpan();
    incomingRequestSpan.name("Incoming Request");

    // todo would prefer to set NA for this (chunked transfer?) scenario
    incomingRequestSpan.tag("message.size", Objects.requireNonNullElse(contentLength, "0"));

    if (xTardisTraceId != null) {
      incomingRequestSpan.tag(Constants.HEADER_X_TARDIS_TRACE_ID, xTardisTraceId);
    }

    incomingRequestSpan.remoteServiceName(applicationName);
    incomingRequestSpan.event("jrqf");
  }

  @AllArgsConstructor
  @Getter
  public static class Config extends AbstractGatewayFilterFactory.NameConfig {
    private String routePathPrefix;
  }
}

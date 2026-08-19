// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.*;
import jumper.Constants;
import jumper.util.HeaderUtil;
import jumper.util.LoadBalancingUtil;
import jumper.util.OauthTokenUtil;
import jumper.util.ObjectMapperUtil;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class JumperConfig {

  private HashMap<String, OauthCredentials> oauth;
  private HashMap<String, BasicAuthCredentials> basicAuth;
  private HashMap<String, List<ConfiguredClaim>> claims;
  private HashMap<String, RouteListener> routeListener;
  private List<String> removeHeaders;
  private GatewayClient gatewayClient;
  private LoadBalancing loadBalancing;

  String targetZoneName;
  String scopes;
  String apiBasePath;
  String consumer;
  String consumerOriginStargate;
  String consumerOriginZone;

  @ToString.Exclude String authorizationToken;
  String externalTokenEndpoint;

  @JsonProperty("issuer")
  String internalTokenEndpoint;

  String clientId;

  @ToString.Exclude String clientSecret;
  Boolean accessTokenForwarding;

  @JsonProperty("realm")
  String realmName;

  String remoteApiUrl;

  @JsonProperty("environment")
  String envName;

  String xSpacegateClientId;

  @ToString.Exclude String xSpacegateClientSecret;
  String xSpacegateScope;

  // calculated routing stuff within requestFilter
  String requestPath;
  String routingPath;
  String finalApiUrl;

  Boolean secondaryFailover = false;

  public static String toJsonBase64(Object o) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = ObjectMapperUtil.getInstance().writeValueAsString(o);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JacksonException e) {
      log.error("can not base64encode object: " + o);
    }

    return jsonConfigBase64;
  }

  private static <T> T fromJsonBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return ObjectMapperUtil.getInstance().readValue(decodedJson, typeReference);
    } catch (JacksonException e) {
      throw new RuntimeException("can not base64decode header: " + jsonConfigBase64);
    }
  }

  public static JumperConfig fromJsonBase64(String jsonConfigBase64) {
    if (StringUtils.isNotBlank(jsonConfigBase64)) {
      return JumperConfig.fromJsonBase64(jsonConfigBase64, new TypeReference<>() {});
    } else {
      return new JumperConfig();
    }
  }

  private void fillWithLegacyHeaders(ServerHttpRequest request) {

    // proxy & real
    if (request.getHeaders().containsHeader(Constants.HEADER_REMOTE_API_URL)) {
      setRemoteApiUrl(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REMOTE_API_URL));
    } else if (Objects.nonNull(loadBalancing) && !loadBalancing.getServers().isEmpty()) {
      setRemoteApiUrl(LoadBalancingUtil.calculateUpstream(loadBalancing.getServers()));
    } else {
      throw new RuntimeException(
          "missing routing information " + Constants.HEADER_REMOTE_API_URL + " / jc.loadBalancing");
    }

    // proxy
    setInternalTokenEndpoint(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ISSUER));
    setClientId(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_CLIENT_ID)); // also external
    setClientSecret(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_CLIENT_SECRET)); // also external

    // real
    setApiBasePath(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_API_BASE_PATH));
    if (request.getHeaders().containsHeader(Constants.HEADER_ACCESS_TOKEN_FORWARDING)) {
      setAccessTokenForwarding(
          Boolean.valueOf(
              HeaderUtil.getLastValueFromHeaderField(
                  request, Constants.HEADER_ACCESS_TOKEN_FORWARDING)));
    }
    setRealmName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REALM));
    if (StringUtils.isBlank(getRealmName())) {
      setRealmName(Constants.DEFAULT_REALM);
    }
    setEnvName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ENVIRONMENT));

    // external oauth
    setScopes(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SCOPES));
    setExternalTokenEndpoint(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_TOKEN_ENDPOINT));
    setXSpacegateClientId(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID));
    setXSpacegateClientSecret(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET));
    setXSpacegateScope(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_SCOPE));

    // processing
    setAuthorizationToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> authorizationTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(authorizationToken);
    setConsumer(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    setConsumerOriginStargate(
        authorizationTokenClaims
            .getBody()
            .get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    setConsumerOriginZone(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));
  }

  public void fillProcessingInfo(ServerHttpRequest request) {
    setAuthorizationToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> authorizationTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(authorizationToken);
    setConsumer(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    setConsumerOriginStargate(
        authorizationTokenClaims
            .getBody()
            .get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    setConsumerOriginZone(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));

    // Spectre stuff
    JumperConfig jc =
        JumperConfig.fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
    this.setRouteListener(jc.getRouteListener());
    this.setGatewayClient(jc.getGatewayClient());

    resolveMissingRealmName();

    // check loadBalancing
    if (Objects.nonNull(loadBalancing) && !loadBalancing.getServers().isEmpty()) {
      setRemoteApiUrl(LoadBalancingUtil.calculateUpstream(loadBalancing.getServers()));
    } else if (Objects.isNull(remoteApiUrl)) {
      throw new RuntimeException("missing routing information jc.remoteApiUrl / jc.loadBalancing");
    }
  }

  public static List<JumperConfig> parseJumperConfigListFrom(ServerHttpRequest request) {

    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isNotBlank(routingConfigBase64)) {
      return JumperConfig.fromJsonBase64(routingConfigBase64, new TypeReference<>() {});
    }

    throw new RuntimeException("can not base64decode header: " + routingConfigBase64);
  }

  public static JumperConfig parseAndFillJumperConfigFrom(ServerHttpRequest request) {

    JumperConfig jc =
        JumperConfig.fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));

    jc.fillWithLegacyHeaders(
        request); // TODO: remove as soon we have completely shifted to json_config

    return jc;
  }

  /**
   * Fills in the realm for configs assembled from {@code routing_config}, the path taken whenever
   * zone failover is configured. The realm can be absent there: the control plane emits the {@code
   * realm} header only for non-failover routes, and a per-entry {@code realm} is omitted when
   * empty. Without a realm, {@code SpectreService} builds its publish URL from {@code null} and the
   * request it was only observing fails.
   *
   * <p>Deliberately additive: a realm already present in the config blob is never overwritten, so
   * the legacy-header path in {@link #fillWithLegacyHeaders(ServerHttpRequest)} keeps its existing
   * precedence and an entry keeps the realm the control plane assigned it.
   */
  private void resolveMissingRealmName() {
    if (StringUtils.isNotBlank(realmName)) {
      return;
    }

    setRealmName(determineRealmName());
  }

  /**
   * Realm for a {@code routing_config} entry that carries none of its own: the realm embedded in
   * the entry's own issuer, else {@link Constants#DEFAULT_REALM}.
   *
   * <p>Both sources are the routing_config blob itself, and that is deliberate - the realm selects
   * the Horizon destination and the issuer of gateway-signed tokens, so it must not be sourced from
   * a channel the caller can write. Two channels are excluded for that reason:
   *
   * <ul>
   *   <li>the inbound {@code realm} header - the control plane's last-mile-security feature is the
   *       only thing that adds or replaces it, and that feature is skipped for failover routes, so
   *       on this path a caller-supplied header arrives unsanitised;
   *   <li>{@code gatewayClient.issuer} - {@code gatewayClient} comes from the {@code jumper_config}
   *       header, which the control plane emits <em>instead of</em> {@code routing_config} and
   *       never alongside it, so on this path its only producer is the caller.
   * </ul>
   *
   * <p>Package-private for testing.
   */
  String determineRealmName() {
    if (StringUtils.isNotBlank(internalTokenEndpoint)
        && internalTokenEndpoint.contains(Constants.REALMS_PATH_SEGMENT)) {
      return internalTokenEndpoint.replaceFirst(".*" + Constants.REALMS_PATH_SEGMENT, "");
    }

    log.warn(
        "no realm resolvable for routing_config entry, falling back to {}",
        Constants.DEFAULT_REALM);
    return Constants.DEFAULT_REALM;
  }

  public boolean isListenerMatched() {
    return Objects.nonNull(getRouteListener())
        && Objects.nonNull(getRouteListener().get(getConsumer()));
  }

  public Optional<BasicAuthCredentials> getBasicAuthCredentials() {
    if (Objects.isNull(getBasicAuth())) {
      return Optional.empty();
    }

    BasicAuthCredentials consumerEntry = getBasicAuth().get(getConsumer());
    if (Objects.nonNull(consumerEntry)) {
      return Optional.of(consumerEntry);
    }

    return Optional.ofNullable(getBasicAuth().get(Constants.BASIC_AUTH_PROVIDER_KEY));
  }

  public Optional<OauthCredentials> getOauthCredentials() {
    if (Objects.isNull(getOauth())) {
      return Optional.empty();
    }

    OauthCredentials consumerEntry = getOauth().get(getConsumer());
    OauthCredentials providerDefault = getOauth().get(Constants.OAUTH_PROVIDER_KEY);

    if (Objects.isNull(consumerEntry)) {
      return Optional.ofNullable(providerDefault);
    }

    return Optional.of(applyScopeOnlyOverride(consumerEntry, providerDefault));
  }

  /**
   * A consumer entry that carries nothing but scopes cannot request a token on its own. In that
   * case the provider's credentials are used together with the consumer's scopes, so that a
   * subscription can narrow the scopes of the provider's external identity provider configuration.
   *
   * <p>The override is only applied when consumer and provider entry agree on whether a grantType
   * is present, because that flag decides which token path {@code UpstreamOAuthFilter} takes. Never
   * switching the path keeps the {@code X-Spacegate-*} header precedence of the legacy path intact.
   */
  private OauthCredentials applyScopeOnlyOverride(
      OauthCredentials consumerEntry, OauthCredentials providerDefault) {

    if (consumerEntry.hasAnyCredentialField()
        || StringUtils.isBlank(consumerEntry.getScopes())
        || Objects.isNull(providerDefault)
        || StringUtils.isBlank(consumerEntry.getGrantType())
        || StringUtils.isBlank(providerDefault.getGrantType())) {
      return consumerEntry;
    }

    return providerDefault.copyWithScopes(consumerEntry.getScopes());
  }

  public String getSecurityScopes() {
    Optional<OauthCredentials> oauthCredentials = getOauthCredentials();
    return oauthCredentials.map(OauthCredentials::getScopes).orElse(null);
  }

  @JsonIgnore
  public Optional<ConfiguredClaim> getConfiguredAudienceClaim() {
    if (Objects.isNull(claims)) {
      return Optional.empty();
    }

    List<ConfiguredClaim> defaultClaims = claims.get(Constants.CLAIMS_DEFAULT_KEY);
    if (Objects.isNull(defaultClaims)) {
      return Optional.empty();
    }

    List<ConfiguredClaim> audienceClaims =
        defaultClaims.stream()
            .filter(Objects::nonNull)
            .filter(claim -> Constants.TOKEN_CLAIM_AUD.equals(claim.getKey()))
            .toList();

    // The control plane schema allows a single aud claim, so additional entries indicate config
    // drift. The first entry still wins, so warn instead of failing an otherwise valid request.
    if (audienceClaims.size() > 1) {
      log.warn(
          "Configured claims contain {} aud entries, only the first one is applied",
          audienceClaims.size());
    }

    return audienceClaims.stream().findFirst();
  }

  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ConfiguredClaim {
    private String key;
    private String value;
    private String valueFrom;
  }
}

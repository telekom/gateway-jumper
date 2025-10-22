// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.*;
import jumper.Constants;
import jumper.util.HeaderUtil;
import jumper.util.LoadBalancingUtil;
import jumper.util.OauthTokenUtil;
import jumper.util.ObjectMapperUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
public class JumperConfig {

  private HashMap<String, OauthCredentials> oauth;
  private HashMap<String, BasicAuthCredentials> basicAuth;
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
  String consumerToken;
  String externalTokenEndpoint;

  @JsonProperty("issuer")
  String internalTokenEndpoint;

  String clientId;
  String clientSecret;
  Boolean accessTokenForwarding;

  @JsonProperty("realm")
  String realmName;

  String remoteApiUrl;

  @JsonProperty("environment")
  String envName;

  String xSpacegateClientId;
  String xSpacegateClientSecret;
  String xSpacegateScope;

  // calculated routing stuff within requestFilter
  String requestPath;
  String routingPath;
  String finalApiUrl;

  Boolean secondaryFailover = false;

  @JsonIgnore
  public static String toJsonBase64(Object o) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = ObjectMapperUtil.getInstance().writeValueAsString(o);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JsonProcessingException e) {
      log.error("can not base64encode object: " + o);
    }

    return jsonConfigBase64;
  }

  @JsonIgnore
  private static <T> T fromJsonBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return ObjectMapperUtil.getInstance().readValue(decodedJson, typeReference);
    } catch (JsonProcessingException e) {
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

  @JsonIgnore
  private void fillWithLegacyHeaders(ServerHttpRequest request) {

    // proxy & real
    if (request.getHeaders().containsKey(Constants.HEADER_REMOTE_API_URL)) {
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
    if (request.getHeaders().containsKey(Constants.HEADER_ACCESS_TOKEN_FORWARDING)) {
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
    setConsumerToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(consumerToken));
    setConsumer(consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    setConsumerOriginStargate(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    setConsumerOriginZone(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));
  }

  @JsonIgnore
  public void fillProcessingInfo(ServerHttpRequest request) {
    setConsumerToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(consumerToken));
    setConsumer(consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    setConsumerOriginStargate(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    setConsumerOriginZone(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));

    // Spectre stuff
    JumperConfig jc =
        JumperConfig.fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
    this.setRouteListener(jc.getRouteListener());
    this.setGatewayClient(jc.getGatewayClient());

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

  @JsonIgnore
  public static JumperConfig parseAndFillJumperConfigFrom(ServerHttpRequest request) {

    JumperConfig jc =
        JumperConfig.fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));

    jc.fillWithLegacyHeaders(
        request); // TODO: remove as soon we have completely shifted to json_config

    return jc;
  }

  public boolean isListenerMatched() {
    return Objects.nonNull(getRouteListener())
        && Objects.nonNull(getRouteListener().get(getConsumer()));
  }

  public Optional<BasicAuthCredentials> getBasicAuthCredentials() {
    if (Objects.nonNull(getBasicAuth())) {

      if (getBasicAuth().containsKey(getConsumer())) {
        return Optional.of(getBasicAuth().get(getConsumer()));
      }

      if (getBasicAuth().containsKey(Constants.BASIC_AUTH_PROVIDER_KEY)) {
        return Optional.of(getBasicAuth().get(Constants.BASIC_AUTH_PROVIDER_KEY));
      }
    }

    return Optional.empty();
  }

  public Optional<OauthCredentials> getOauthCredentials() {
    if (Objects.nonNull(getOauth())) {
      if (getOauth().containsKey(getConsumer())) {
        return Optional.of(getOauth().get(getConsumer()));
      }

      if (getOauth().containsKey(Constants.OAUTH_PROVIDER_KEY)) {
        return Optional.of(getOauth().get(Constants.OAUTH_PROVIDER_KEY));
      }
    }

    return Optional.empty();
  }

  public String getSecurityScopes() {
    Optional<OauthCredentials> oauthCredentials = getOauthCredentials();
    return oauthCredentials.map(OauthCredentials::getScopes).orElse(null);
  }
}

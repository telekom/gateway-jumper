// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import java.util.*;
import javax.validation.constraints.NotNull;
import jumper.Constants;
import jumper.service.HeaderUtil;
import jumper.service.OauthTokenUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JumperConfig {

  private HashMap<String, OauthCredentials> oauth;
  private HashMap<String, BasicAuthCredentials> basicAuth;
  private HashMap<String, RouteListener> routeListener;
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
  public static String toBase64(Object o) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = new ObjectMapper().writeValueAsString(o);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

    return jsonConfigBase64;
  }

  @JsonIgnore
  private static <T> T fromBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .readValue(decodedJson, typeReference);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("can not base64decode header: " + jsonConfigBase64);
    }
  }

  private static JumperConfig fromBase64(String jsonConfigBase64) {
    if (StringUtils.isNotBlank(jsonConfigBase64)) {
      return JumperConfig.fromBase64(jsonConfigBase64, new TypeReference<>() {});
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
      setRemoteApiUrl(calculateUpstream(loadBalancing.getServers()));
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
    Jwt<Header, Claims> consumerTokenClaims =
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
    Jwt<Header, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(consumerToken));
    setConsumer(consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    setConsumerOriginStargate(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    setConsumerOriginZone(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));

    // Spectre stuff
    JumperConfig jc =
        JumperConfig.fromBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
    this.setRouteListener(jc.getRouteListener());
    this.setGatewayClient(jc.getGatewayClient());

    // check loadBalancing
    if (Objects.nonNull(loadBalancing) && !loadBalancing.getServers().isEmpty()) {
      setRemoteApiUrl(calculateUpstream(loadBalancing.getServers()));
    } else if (Objects.isNull(remoteApiUrl)) {
      throw new RuntimeException("missing routing information jc.remoteApiUrl / jc.loadBalancing");
    }
  }

  public static List<JumperConfig> parseJumperConfigListFrom(ServerHttpRequest request) {

    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isNotBlank(routingConfigBase64)) {
      return JumperConfig.fromBase64(routingConfigBase64, new TypeReference<>() {});
    }

    throw new RuntimeException("can not base64decode header: " + routingConfigBase64);
  }

  @JsonIgnore
  public static JumperConfig parseAndFillJumperConfigFrom(ServerHttpRequest request) {

    JumperConfig jc =
        JumperConfig.fromBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));

    jc.fillWithLegacyHeaders(
        request); // TODO: remove as soon we have completely shifted to json_config

    return jc;
  }

  @JsonIgnore
  public static JumperConfig parseJumperConfigFrom(ServerWebExchange exchange) {

    return JumperConfig.fromBase64(exchange.getAttribute(Constants.HEADER_JUMPER_CONFIG));
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

  private static String calculateUpstream(@NotNull List<Server> servers) {
    // Sum total of weights
    double total = 0;
    for (Server server : servers) {
      total += server.getWeight();
    }

    // Random a number between [1, total]
    double random = Math.ceil(Math.random() * total);

    // Seek cursor to find which area the random is in
    double cursor = 0;
    for (Server server : servers) {
      cursor += server.getWeight();
      if (cursor >= random) {
        return server.getUpstream();
      }
    }

    throw new RuntimeException("can not calculate upstream");
  }
}

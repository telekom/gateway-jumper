// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jumper.Constants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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

  public boolean isListenerMatched() {
    return Objects.nonNull(getRouteListener())
        && Objects.nonNull(getRouteListener().get(getConsumer()));
  }

  public Optional<BasicAuthCredentials> getBasicAuthCredentials() {
    if (basicAuth == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(basicAuth.get(consumer))
        .or(() -> Optional.ofNullable(basicAuth.get(Constants.BASIC_AUTH_PROVIDER_KEY)));
  }

  public Optional<OauthCredentials> getOauthCredentials() {
    if (oauth == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(oauth.get(consumer))
        .or(() -> Optional.ofNullable(oauth.get(Constants.OAUTH_PROVIDER_KEY)));
  }

  public String getSecurityScopes() {
    Optional<OauthCredentials> oauthCredentials = getOauthCredentials();
    return oauthCredentials.map(OauthCredentials::getScopes).orElse(null);
  }
}

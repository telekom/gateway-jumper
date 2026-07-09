// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import jumper.Constants;
import jumper.service.JumperConfigHeaderReader;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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
  String authorizationToken;
  String externalTokenEndpoint;

  @JsonProperty("issuer")
  String internalTokenEndpoint;

  String clientId;
  String clientSecret;
  Boolean accessTokenForwarding;

  // Mesh-route discriminator set by the control plane in the jumper_config / routing_config blob.
  Boolean mesh;

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

  public static String toJsonBase64(Object value) {
    return JumperConfigHeaderReader.toJsonBase64(value);
  }

  public static JumperConfig fromJsonBase64(String jsonConfigBase64) {
    return JumperConfigHeaderReader.fromJsonBase64(jsonConfigBase64);
  }

  public boolean isListenerMatched() {
    return Objects.nonNull(getRouteListener())
        && Objects.nonNull(getRouteListener().get(getConsumer()));
  }

  /**
   * Whether this route is a cross-zone mesh (proxy) route and should generate a mesh LMS token
   * instead of a provider LMS token.
   *
   * <p>{@code mesh} is the canonical signal set by the control plane in the jumper_config /
   * routing_config blob. The {@code internalTokenEndpoint} (issuer header) clause is a transitional
   * fallback for pre-migration proxy routes that still carry {@code issuer} but no {@code mesh}.
   *
   * <p>TODO: drop the {@code internalTokenEndpoint} clause once the control-plane migration for the
   * mesh LMS feature is fully complete.
   */
  @JsonIgnore
  public boolean isMeshRoute() {
    return Boolean.TRUE.equals(mesh) || Objects.nonNull(internalTokenEndpoint);
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

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import lombok.Data;

/**
 * Wire representation of one decoded {@code jumper_config} object or one selected entry from {@code
 * routing_config}.
 *
 * <p>This model contains only control-plane configuration. It must not be enriched with legacy
 * request headers, incoming token claims, or calculated request state. Consumers that need
 * compatibility fallbacks should use {@code EffectiveRequestConfigResolver} with the separate
 * {@code HeaderConfig}.
 */
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

  @JsonProperty("tokenEndpoint")
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
}

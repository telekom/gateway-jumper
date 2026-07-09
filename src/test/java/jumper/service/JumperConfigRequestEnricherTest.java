// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.LoadBalancing;
import jumper.model.config.Server;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class JumperConfigRequestEnricherTest {

  private static final String CONFIG_REMOTE_API_URL = "http://config.example.com/api";
  private static final String HEADER_REMOTE_API_URL = "http://header.example.com/api";
  private static final String CONFIG_API_BASE_PATH = "/config-api";
  private static final String HEADER_API_BASE_PATH = "/header-api";
  private static final String CONFIG_ISSUER = "http://config-idp/auth/realms/config";
  private static final String HEADER_ISSUER = "http://header-idp/auth/realms/header";
  private static final String CONFIG_CLIENT_ID = "config-client";
  private static final String HEADER_CLIENT_ID = "header-client";
  private static final String CONFIG_CLIENT_SECRET = "config-secret";
  private static final String HEADER_CLIENT_SECRET = "header-secret";
  private static final String CONFIG_REALM = "config-realm";
  private static final String HEADER_REALM = "header-realm";
  private static final String CONFIG_ENVIRONMENT = "config-env";
  private static final String HEADER_ENVIRONMENT = "header-env";
  private static final String CONFIG_TOKEN_ENDPOINT = "http://config-idp/token";
  private static final String HEADER_TOKEN_ENDPOINT = "http://header-idp/token";
  private static final String CONFIG_SCOPES = "config.scope";
  private static final String HEADER_SCOPES = "header.scope";
  private static final String CONFIG_X_SPACEGATE_CLIENT_ID = "config-spacegate-client";
  private static final String HEADER_X_SPACEGATE_CLIENT_ID = "header-spacegate-client";
  private static final String CONFIG_X_SPACEGATE_CLIENT_SECRET = "config-spacegate-secret";
  private static final String HEADER_X_SPACEGATE_CLIENT_SECRET = "header-spacegate-secret";
  private static final String CONFIG_X_SPACEGATE_SCOPE = "config-spacegate.scope";
  private static final String HEADER_X_SPACEGATE_SCOPE = "header-spacegate.scope";

  private final JumperConfigRequestEnricher requestEnricher =
      new JumperConfigRequestEnricher(new JumperConfigHeaderReader());

  @Test
  void applySingleRouteHeaderFallbacks_keepsExistingConfigValues() {
    // arrange
    JumperConfig config = configWithAllHeaderBackedFields();
    ServerHttpRequest request = requestWithAllLegacyHeaders();

    // act
    requestEnricher.applySingleRouteHeaderFallbacks(config, request);

    // assert
    assertEquals(CONFIG_REMOTE_API_URL, config.getRemoteApiUrl());
    assertEquals(CONFIG_API_BASE_PATH, config.getApiBasePath());
    assertEquals(CONFIG_ISSUER, config.getInternalTokenEndpoint());
    assertEquals(CONFIG_CLIENT_ID, config.getClientId());
    assertEquals(CONFIG_CLIENT_SECRET, config.getClientSecret());
    assertEquals(CONFIG_REALM, config.getRealmName());
    assertEquals(CONFIG_ENVIRONMENT, config.getEnvName());
    assertEquals(CONFIG_TOKEN_ENDPOINT, config.getExternalTokenEndpoint());
    assertEquals(CONFIG_SCOPES, config.getScopes());
    assertEquals(CONFIG_X_SPACEGATE_CLIENT_ID, config.getXSpacegateClientId());
    assertEquals(CONFIG_X_SPACEGATE_CLIENT_SECRET, config.getXSpacegateClientSecret());
    assertEquals(CONFIG_X_SPACEGATE_SCOPE, config.getXSpacegateScope());
    assertEquals(Boolean.TRUE, config.getAccessTokenForwarding());
  }

  @Test
  void applySingleRouteHeaderFallbacks_fillsMissingValuesFromLegacyHeaders() {
    // arrange
    JumperConfig config = new JumperConfig();
    ServerHttpRequest request = requestWithAllLegacyHeaders();

    // act
    requestEnricher.applySingleRouteHeaderFallbacks(config, request);

    // assert
    assertEquals(HEADER_REMOTE_API_URL, config.getRemoteApiUrl());
    assertEquals(HEADER_API_BASE_PATH, config.getApiBasePath());
    assertEquals(HEADER_ISSUER, config.getInternalTokenEndpoint());
    assertEquals(HEADER_CLIENT_ID, config.getClientId());
    assertEquals(HEADER_CLIENT_SECRET, config.getClientSecret());
    assertEquals(HEADER_REALM, config.getRealmName());
    assertEquals(HEADER_ENVIRONMENT, config.getEnvName());
    assertEquals(HEADER_TOKEN_ENDPOINT, config.getExternalTokenEndpoint());
    assertEquals(HEADER_SCOPES, config.getScopes());
    assertEquals(HEADER_X_SPACEGATE_CLIENT_ID, config.getXSpacegateClientId());
    assertEquals(HEADER_X_SPACEGATE_CLIENT_SECRET, config.getXSpacegateClientSecret());
    assertEquals(HEADER_X_SPACEGATE_SCOPE, config.getXSpacegateScope());
    assertEquals(Boolean.FALSE, config.getAccessTokenForwarding());
  }

  @Test
  void resolveSingleRouteRemoteApiUrl_keepsConfigRemoteApiUrlWhenNoLoadBalancingExists() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setRemoteApiUrl(CONFIG_REMOTE_API_URL);

    // act
    requestEnricher.resolveSingleRouteRemoteApiUrl(config);

    // assert
    assertEquals(CONFIG_REMOTE_API_URL, config.getRemoteApiUrl());
  }

  @Test
  void resolveSingleRouteRemoteApiUrl_prefersLoadBalancingOverLegacyRemoteApiUrlFallback() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setRemoteApiUrl(HEADER_REMOTE_API_URL);
    config.setLoadBalancing(loadBalancingWithSingleServer(CONFIG_REMOTE_API_URL));

    // act
    requestEnricher.resolveSingleRouteRemoteApiUrl(config);

    // assert
    assertEquals(CONFIG_REMOTE_API_URL, config.getRemoteApiUrl());
  }

  @Test
  void applySelectedRoutingConfigFallbacks_keepsSelectedRoutingConfigValues() {
    // arrange
    JumperConfig config = configWithAllHeaderBackedFields();
    ServerHttpRequest request = requestWithAllLegacyHeaders();

    // act
    requestEnricher.applySelectedRoutingConfigFallbacks(config, request);

    // assert
    assertEquals(CONFIG_REMOTE_API_URL, config.getRemoteApiUrl());
    assertEquals(CONFIG_API_BASE_PATH, config.getApiBasePath());
    assertEquals(CONFIG_ISSUER, config.getInternalTokenEndpoint());
    assertEquals(CONFIG_CLIENT_ID, config.getClientId());
    assertEquals(CONFIG_CLIENT_SECRET, config.getClientSecret());
    assertEquals(CONFIG_REALM, config.getRealmName());
    assertEquals(CONFIG_ENVIRONMENT, config.getEnvName());
    assertEquals(CONFIG_TOKEN_ENDPOINT, config.getExternalTokenEndpoint());
    assertEquals(CONFIG_SCOPES, config.getScopes());
    assertEquals(Boolean.TRUE, config.getAccessTokenForwarding());
  }

  @Test
  void applySelectedRoutingConfigFallbacks_onlyCopiesRealmAndEnvironmentFromLegacyHeaders() {
    // arrange
    JumperConfig config = new JumperConfig();
    ServerHttpRequest request = requestWithAllLegacyHeaders();

    // act
    requestEnricher.applySelectedRoutingConfigFallbacks(config, request);

    // assert
    assertNull(config.getRemoteApiUrl());
    assertNull(config.getApiBasePath());
    assertNull(config.getInternalTokenEndpoint());
    assertNull(config.getClientId());
    assertNull(config.getClientSecret());
    assertEquals(HEADER_REALM, config.getRealmName());
    assertEquals(HEADER_ENVIRONMENT, config.getEnvName());
    assertNull(config.getExternalTokenEndpoint());
    assertNull(config.getScopes());
    assertNull(config.getAccessTokenForwarding());
  }

  @Test
  void
      applySelectedRoutingConfigFallbacks_usesLegacyIssuerRealmWhenConfigAndHeaderRealmAreMissing() {
    // TODO: remove after mesh LMS phase 2 completes and the legacy issuer realm fallback is
    // deleted.
    // arrange
    JumperConfig config = new JumperConfig();
    config.setInternalTokenEndpoint("http://localhost:1081/auth/realms/sit");
    ServerHttpRequest request = MockServerHttpRequest.get("/").build();

    // act
    requestEnricher.applySelectedRoutingConfigFallbacks(config, request);

    // assert
    assertEquals("sit", config.getRealmName());
  }

  @Test
  void applySelectedRoutingConfigFallbacks_usesDefaultRealmWhenNoRealmSourceExists() {
    // arrange
    JumperConfig config = new JumperConfig();
    ServerHttpRequest request = MockServerHttpRequest.get("/").build();

    // act
    requestEnricher.applySelectedRoutingConfigFallbacks(config, request);

    // assert
    assertEquals(Constants.DEFAULT_REALM, config.getRealmName());
  }

  private static JumperConfig configWithAllHeaderBackedFields() {
    JumperConfig config = new JumperConfig();
    config.setRemoteApiUrl(CONFIG_REMOTE_API_URL);
    config.setApiBasePath(CONFIG_API_BASE_PATH);
    config.setInternalTokenEndpoint(CONFIG_ISSUER);
    config.setClientId(CONFIG_CLIENT_ID);
    config.setClientSecret(CONFIG_CLIENT_SECRET);
    config.setRealmName(CONFIG_REALM);
    config.setEnvName(CONFIG_ENVIRONMENT);
    config.setExternalTokenEndpoint(CONFIG_TOKEN_ENDPOINT);
    config.setScopes(CONFIG_SCOPES);
    config.setXSpacegateClientId(CONFIG_X_SPACEGATE_CLIENT_ID);
    config.setXSpacegateClientSecret(CONFIG_X_SPACEGATE_CLIENT_SECRET);
    config.setXSpacegateScope(CONFIG_X_SPACEGATE_SCOPE);
    config.setAccessTokenForwarding(true);
    return config;
  }

  private static ServerHttpRequest requestWithAllLegacyHeaders() {
    return MockServerHttpRequest.get("/")
        .header(Constants.HEADER_REMOTE_API_URL, HEADER_REMOTE_API_URL)
        .header(Constants.HEADER_API_BASE_PATH, HEADER_API_BASE_PATH)
        .header(Constants.HEADER_ISSUER, HEADER_ISSUER)
        .header(Constants.HEADER_CLIENT_ID, HEADER_CLIENT_ID)
        .header(Constants.HEADER_CLIENT_SECRET, HEADER_CLIENT_SECRET)
        .header(Constants.HEADER_REALM, HEADER_REALM)
        .header(Constants.HEADER_ENVIRONMENT, HEADER_ENVIRONMENT)
        .header(Constants.HEADER_TOKEN_ENDPOINT, HEADER_TOKEN_ENDPOINT)
        .header(Constants.HEADER_CLIENT_SCOPES, HEADER_SCOPES)
        .header(Constants.HEADER_X_SPACEGATE_CLIENT_ID, HEADER_X_SPACEGATE_CLIENT_ID)
        .header(Constants.HEADER_X_SPACEGATE_CLIENT_SECRET, HEADER_X_SPACEGATE_CLIENT_SECRET)
        .header(Constants.HEADER_X_SPACEGATE_SCOPE, HEADER_X_SPACEGATE_SCOPE)
        .header(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false")
        .build();
  }

  private static LoadBalancing loadBalancingWithSingleServer(String upstream) {
    LoadBalancing loadBalancing = new LoadBalancing();
    loadBalancing.setServers(List.of(new Server(upstream, 1.0)));
    return loadBalancing;
  }
}

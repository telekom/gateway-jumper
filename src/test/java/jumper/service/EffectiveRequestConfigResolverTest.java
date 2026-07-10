// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.LoadBalancing;
import jumper.model.config.OauthCredentials;
import jumper.model.config.Server;
import jumper.model.request.HeaderConfig;
import org.junit.jupiter.api.Test;

class EffectiveRequestConfigResolverTest {

  private final EffectiveRequestConfigResolver resolver = new EffectiveRequestConfigResolver();

  @Test
  void resolveUpstreamUrl_prefersLoadBalancingOverConfiguredAndLegacyUrls() {
    // arrange
    JumperConfig config = new JumperConfig();
    config.setRemoteApiUrl("https://configured.example.com");
    LoadBalancing loadBalancing = new LoadBalancing();
    loadBalancing.setServers(List.of(new Server("https://balanced.example.com", 100.0)));
    config.setLoadBalancing(loadBalancing);
    HeaderConfig headers = headers(false, "https://legacy.example.com", null, null, null, null);

    // act
    String upstreamUrl = resolver.resolveUpstreamUrl(config, headers);

    // assert
    assertEquals("https://balanced.example.com", upstreamUrl);
  }

  @Test
  void resolveTargetValues_doesNotInheritLegacyHeadersForSelectedRoutingConfig() {
    // arrange
    JumperConfig config = new JumperConfig();
    HeaderConfig headers =
        headers(
            true,
            "https://legacy.example.com",
            "/legacy",
            "https://issuer.example.com/realms/legacy",
            "https://tokens.example.com",
            null);

    // act
    String apiBasePath = resolver.resolveApiBasePath(config, headers);
    String internalTokenEndpoint = resolver.resolveInternalTokenEndpoint(config, headers);
    String externalTokenEndpoint = resolver.resolveExternalTokenEndpoint(config, headers);

    // assert
    assertNull(apiBasePath);
    assertNull(internalTokenEndpoint);
    assertNull(externalTokenEndpoint);
    assertThrows(RuntimeException.class, () -> resolver.resolveUpstreamUrl(config, headers));
  }

  @Test
  void resolveRealmName_usesConfigThenHeaderThenIssuerThenDefault() {
    // arrange
    JumperConfig config = new JumperConfig();
    HeaderConfig headers =
        headers(
            false,
            null,
            null,
            "https://issuer.example.com/realms/from-issuer",
            null,
            "from-header");

    // act
    String headerRealm = resolver.resolveRealmName(config, headers);
    config.setRealmName("from-config");
    String configRealm = resolver.resolveRealmName(config, headers);
    config.setRealmName(null);
    String issuerRealm =
        resolver.resolveRealmName(
            config,
            headers(
                false, null, null, "https://issuer.example.com/realms/from-issuer", null, null));
    String defaultRealm =
        resolver.resolveRealmName(config, headers(false, null, null, null, null, null));

    // assert
    assertEquals("from-header", headerRealm);
    assertEquals("from-config", configRealm);
    assertEquals("from-issuer", issuerRealm);
    assertEquals(Constants.DEFAULT_REALM, defaultRealm);
  }

  @Test
  void resolveEnvironment_prefersConfigAndAllowsHeaderFallbackForSelectedRoutingConfig() {
    // arrange
    JumperConfig config = new JumperConfig();
    HeaderConfig headers = headers(true, null, null, null, null, null, "header-environment");

    // act
    String headerEnvironment = resolver.resolveEnvironment(config, headers);
    config.setEnvName("config-environment");
    String configEnvironment = resolver.resolveEnvironment(config, headers);

    // assert
    assertEquals("header-environment", headerEnvironment);
    assertEquals("config-environment", configEnvironment);
  }

  @Test
  void resolveLmsSecurityScopes_prefersClientAndFallsBackToProviderCredentials() {
    // arrange
    OauthCredentials clientCredentials = new OauthCredentials();
    clientCredentials.setScopes("client-scope");
    OauthCredentials providerCredentials = new OauthCredentials();
    providerCredentials.setScopes("provider-scope");
    JumperConfig config = new JumperConfig();
    config.setOauth(
        new HashMap<>(
            Map.of(
                "client", clientCredentials, Constants.OAUTH_PROVIDER_KEY, providerCredentials)));

    // act
    String clientScopes = resolver.resolveLmsSecurityScopes(config, "client");
    String providerScopes = resolver.resolveLmsSecurityScopes(config, "other-client");

    // assert
    assertEquals("client-scope", clientScopes);
    assertEquals("provider-scope", providerScopes);
  }

  private static HeaderConfig headers(
      boolean hasRoutingConfigHeader,
      String remoteApiUrl,
      String apiBasePath,
      String issuer,
      String tokenEndpoint,
      String realm) {
    return headers(
        hasRoutingConfigHeader, remoteApiUrl, apiBasePath, issuer, tokenEndpoint, realm, null);
  }

  private static HeaderConfig headers(
      boolean hasRoutingConfigHeader,
      String remoteApiUrl,
      String apiBasePath,
      String issuer,
      String tokenEndpoint,
      String realm,
      String environment) {
    return new HeaderConfig(
        hasRoutingConfigHeader,
        remoteApiUrl,
        issuer,
        null,
        null,
        apiBasePath,
        realm,
        environment,
        tokenEndpoint,
        null,
        null,
        null,
        null);
  }
}

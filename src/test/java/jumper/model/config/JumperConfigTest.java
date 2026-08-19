// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Optional;
import jumper.Constants;
import jumper.util.ObjectMapperUtil;
import jumper.util.TokenUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import tools.jackson.databind.json.JsonMapper;

class JumperConfigTest {

  @BeforeAll
  static void initObjectMapper() {
    // fillProcessingInfo parses the jumper_config header and the JWT header via ObjectMapperUtil,
    // whose static holder is normally populated by Spring. Populate it for this context-less test.
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

  private static final String CONSUMER = "eni--local-team--local-app";
  private static final String CONSUMER_SCOPE = "consumer_scope";
  private static final String PROVIDER_SCOPE = "provider_scope";

  private JumperConfig jumperConfig(
      OauthCredentials consumerEntry, OauthCredentials providerEntry) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    if (consumerEntry != null) {
      oauth.put(CONSUMER, consumerEntry);
    }
    if (providerEntry != null) {
      oauth.put(Constants.OAUTH_PROVIDER_KEY, providerEntry);
    }
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);
    jc.setOauth(oauth);
    return jc;
  }

  private OauthCredentials scopeOnlyEntry(String scopes, String grantType) {
    OauthCredentials oc = new OauthCredentials();
    oc.setScopes(scopes);
    oc.setGrantType(grantType);
    oc.setTokenRequest("HEADER");
    return oc;
  }

  private OauthCredentials providerEntry(String grantType) {
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId("provider_client");
    oc.setClientSecret("provider_secret");
    oc.setScopes(PROVIDER_SCOPE);
    oc.setGrantType(grantType);
    oc.setTokenRequest("HEADER");
    return oc;
  }

  private static final String ENTRY_REALM = "entry-realm";
  private static final String HEADER_REALM = "header-realm";
  private static final String MESH_REALM = "mesh-realm";
  private static final String GATEWAY_CLIENT_REALM = "gateway-client-realm";

  private static JumperConfig configWithMeshIssuer(String realm) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/" + realm);
    return jc;
  }

  private static JumperConfig configWithGatewayClientIssuer(String realm) {
    GatewayClient gatewayClient = new GatewayClient();
    gatewayClient.setIssuer("http://localhost:1081/auth/realms/" + realm);
    JumperConfig jc = new JumperConfig();
    jc.setGatewayClient(gatewayClient);
    return jc;
  }

  /**
   * A jumper_config header value carrying a gateway client, as the legacy control plane sends it.
   */
  private static String jumperConfigHeaderWithGatewayClientIssuer(String realm) {
    return JumperConfig.toJsonBase64(configWithGatewayClientIssuer(realm));
  }

  private static ServerHttpRequest requestWith(String realmHeader, String jumperConfigHeader) {
    MockServerHttpRequest.BodyBuilder builder =
        MockServerHttpRequest.method(HttpMethod.DELETE, "/listener/services/1")
            .header(Constants.HEADER_AUTHORIZATION, "Bearer " + TokenUtil.getConsumerAccessToken());
    if (realmHeader != null) {
      builder.header(Constants.HEADER_REALM, realmHeader);
    }
    if (jumperConfigHeader != null) {
      builder.header(Constants.HEADER_JUMPER_CONFIG, jumperConfigHeader);
    }
    return builder.build();
  }

  private static JumperConfig routingConfigEntry(String entryRealm, String meshRealm) {
    JumperConfig jc = meshRealm == null ? new JumperConfig() : configWithMeshIssuer(meshRealm);
    jc.setRealmName(entryRealm);
    // fillProcessingInfo requires routing information or it throws
    jc.setRemoteApiUrl("http://localhost:1080/provider");
    return jc;
  }

  @Test
  void determineRealmName_derivesRealmFromTheEntryIssuer() {
    // arrange
    JumperConfig jc = configWithMeshIssuer(MESH_REALM);

    // act & assert
    assertEquals(MESH_REALM, jc.determineRealmName());
  }

  @Test
  void determineRealmName_ignoresGatewayClientIssuer() {
    // arrange: gatewayClient comes from the jumper_config header, which the control plane does not
    // emit alongside routing_config - on this path only the caller can supply it
    JumperConfig jc = configWithGatewayClientIssuer(GATEWAY_CLIENT_REALM);

    // act & assert
    assertEquals(Constants.DEFAULT_REALM, jc.determineRealmName());
  }

  @Test
  void determineRealmName_fallsBackToDefaultRealmWhenNoIssuerExists() {
    // arrange
    JumperConfig jc = new JumperConfig();

    // act & assert
    assertEquals(Constants.DEFAULT_REALM, jc.determineRealmName());
  }

  @Test
  void determineRealmName_fallsBackToDefaultRealmWhenIssuerCarriesNoRealmSegment() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/no-realm-here");

    // act & assert
    assertEquals(Constants.DEFAULT_REALM, jc.determineRealmName());
  }

  @Test
  void fillProcessingInfo_keepsTheEntryRealmDespiteEveryConflictingSource() {
    // arrange: the selected routing_config entry carries its own realm, while the request offers a
    // conflicting realm header, a conflicting jumper_config gateway client and a conflicting mesh
    // issuer. The entry must win - this is the non-overwrite invariant.
    JumperConfig entry = routingConfigEntry(ENTRY_REALM, MESH_REALM);

    // act
    entry.fillProcessingInfo(
        requestWith(HEADER_REALM, jumperConfigHeaderWithGatewayClientIssuer(GATEWAY_CLIENT_REALM)));

    // assert
    assertEquals(ENTRY_REALM, entry.getRealmName());
  }

  @Test
  void fillProcessingInfo_ignoresTheRealmHeaderAndPrefersTheEntryIssuer() {
    // arrange: no realm on the entry, so a fallback is used. The inbound realm header must not be
    // it - the control plane leaves that header untouched on failover routes.
    JumperConfig entry = routingConfigEntry(null, MESH_REALM);

    // act
    entry.fillProcessingInfo(
        requestWith(HEADER_REALM, jumperConfigHeaderWithGatewayClientIssuer(GATEWAY_CLIENT_REALM)));

    // assert
    assertEquals(MESH_REALM, entry.getRealmName());
  }

  @Test
  void fillProcessingInfo_fallsBackToDefaultRealmRatherThanTrustTheRealmHeader() {
    // arrange: nothing trustworthy to derive a realm from, only a caller-supplied realm header
    JumperConfig entry = routingConfigEntry(null, null);

    // act
    entry.fillProcessingInfo(requestWith(HEADER_REALM, null));

    // assert
    assertEquals(Constants.DEFAULT_REALM, entry.getRealmName());
  }

  @Test
  void getOauthCredentials_emptyWhenNoOauthConfigured() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);

    // act & assert
    assertTrue(jc.getOauthCredentials().isEmpty());
  }

  @Test
  void getOauthCredentials_emptyWhenNeitherConsumerNorProviderEntryExists() {
    // arrange
    JumperConfig jc = jumperConfig(null, null);

    // act & assert
    assertTrue(jc.getOauthCredentials().isEmpty());
  }

  @Test
  void getOauthCredentials_returnsProviderEntryWhenConsumerHasNoEntry() {
    // arrange
    OauthCredentials provider = providerEntry("client_credentials");
    JumperConfig jc = jumperConfig(null, provider);

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(provider, resolved.orElseThrow());
  }

  @Test
  void getOauthCredentials_fallsBackToProviderWhenConsumerEntryIsNull() {
    // arrange - a consumer key explicitly mapped to null must not blow up
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    oauth.put(CONSUMER, null);
    OauthCredentials provider = providerEntry("client_credentials");
    oauth.put(Constants.OAUTH_PROVIDER_KEY, provider);
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);
    jc.setOauth(oauth);

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(provider, resolved.orElseThrow());
  }

  @Test
  void getOauthCredentials_returnsConsumerEntryWhenItCarriesCredentials() {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    consumer.setClientId("consumer_client");
    JumperConfig jc = jumperConfig(consumer, providerEntry("client_credentials"));

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(consumer, resolved.orElseThrow());
  }

  @Test
  void getOauthCredentials_scopeOnlyConsumerEntryRidesOnProviderCredentials() {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    OauthCredentials provider = providerEntry("client_credentials");
    JumperConfig jc = jumperConfig(consumer, provider);

    // act
    OauthCredentials resolved = jc.getOauthCredentials().orElseThrow();

    // assert
    assertNotSame(consumer, resolved);
    assertNotSame(provider, resolved);
    assertEquals("provider_client", resolved.getClientId());
    assertEquals("provider_secret", resolved.getClientSecret());
    assertEquals(CONSUMER_SCOPE, resolved.getScopes());
    assertEquals("client_credentials", resolved.getGrantType());
  }

  @Test
  void getOauthCredentials_scopeOverrideLeavesConfiguredEntriesUntouched() {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    OauthCredentials provider = providerEntry("client_credentials");
    JumperConfig jc = jumperConfig(consumer, provider);

    // act
    jc.getOauthCredentials();

    // assert
    assertEquals(PROVIDER_SCOPE, provider.getScopes());
    assertEquals(CONSUMER_SCOPE, consumer.getScopes());
  }

  @ParameterizedTest(name = "consumer scopes = \"{0}\"")
  @NullSource
  @ValueSource(strings = {"", " "})
  void getOauthCredentials_returnsConsumerEntryWhenItHasNoScopes(String scopes) {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(scopes, "client_credentials");
    JumperConfig jc = jumperConfig(consumer, providerEntry("client_credentials"));

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(consumer, resolved.orElseThrow());
  }

  @Test
  void getOauthCredentials_returnsConsumerEntryWhenNoProviderDefaultExists() {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    JumperConfig jc = jumperConfig(consumer, null);

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(consumer, resolved.orElseThrow());
  }

  @ParameterizedTest(name = "consumer grantType = \"{0}\"")
  @NullSource
  @ValueSource(strings = {"", " "})
  void getOauthCredentials_noOverrideWhenConsumerEntryHasNoGrantType(String grantType) {
    // arrange - the consumer entry is on the legacy token path, the override must not move it
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, grantType);
    JumperConfig jc = jumperConfig(consumer, providerEntry("client_credentials"));

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(consumer, resolved.orElseThrow());
  }

  @ParameterizedTest(name = "provider grantType = \"{0}\"")
  @NullSource
  @ValueSource(strings = {"", " "})
  void getOauthCredentials_noOverrideWhenProviderDefaultHasNoGrantType(String grantType) {
    // arrange - taking the provider entry would move the request onto the legacy token path
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    JumperConfig jc = jumperConfig(consumer, providerEntry(grantType));

    // act
    Optional<OauthCredentials> resolved = jc.getOauthCredentials();

    // assert
    assertSame(consumer, resolved.orElseThrow());
  }

  @Test
  void getSecurityScopes_reflectsTheScopeOverride() {
    // arrange
    OauthCredentials consumer = scopeOnlyEntry(CONSUMER_SCOPE, "client_credentials");
    JumperConfig jc = jumperConfig(consumer, providerEntry("client_credentials"));

    // act & assert
    assertEquals(CONSUMER_SCOPE, jc.getSecurityScopes());
  }

  @Test
  void getSecurityScopes_nullWhenNoOauthConfigured() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);

    // act & assert
    assertNull(jc.getSecurityScopes());
  }
}

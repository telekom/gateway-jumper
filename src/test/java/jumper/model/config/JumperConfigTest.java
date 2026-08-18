// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Optional;
import jumper.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class JumperConfigTest {

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

  private JumperConfig configWithGatewayClientIssuer(String issuer) {
    GatewayClient gatewayClient = new GatewayClient();
    gatewayClient.setIssuer(issuer);
    JumperConfig jc = new JumperConfig();
    jc.setGatewayClient(gatewayClient);
    return jc;
  }

  @Test
  void determineRealmName_prefersRealmHeader() {
    // arrange
    JumperConfig jc = configWithGatewayClientIssuer("https://issuer/auth/realms/from-issuer");

    // act & assert
    assertEquals("from-header", jc.determineRealmName("from-header"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void determineRealmName_derivesRealmFromGatewayClientIssuerWhenHeaderIsBlank(String realmHeader) {
    // arrange
    JumperConfig jc = configWithGatewayClientIssuer("https://issuer/auth/realms/from-issuer");

    // act & assert
    assertEquals("from-issuer", jc.determineRealmName(realmHeader));
  }

  @Test
  void determineRealmName_derivesRealmFromMeshIssuerWhenNoGatewayClientExists() {
    // arrange - a zoned routing_config entry: mesh issuer, no realm, no gateway client
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/from-mesh-issuer");

    // act & assert
    assertEquals("from-mesh-issuer", jc.determineRealmName(null));
  }

  @Test
  void determineRealmName_fallsBackToDefaultRealmWhenNoSourceIsAvailable() {
    // arrange
    JumperConfig jc = new JumperConfig();

    // act & assert
    assertEquals(Constants.DEFAULT_REALM, jc.determineRealmName(null));
  }

  @Test
  void determineRealmName_fallsBackToDefaultRealmWhenIssuerCarriesNoRealmSegment() {
    // arrange
    JumperConfig jc = configWithGatewayClientIssuer("https://issuer/auth/no-realm-here");

    // act & assert
    assertEquals(Constants.DEFAULT_REALM, jc.determineRealmName(null));
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

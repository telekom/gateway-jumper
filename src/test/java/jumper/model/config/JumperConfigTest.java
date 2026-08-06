// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import jumper.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class JumperConfigTest {

  private static final String ISSUER = "http://localhost:1081/auth/realms/default";
  private static final String NON_DEFAULT_REALM = "sit";
  private static final String OTHER_REALM = "rv";

  static Stream<Arguments> isMeshRouteCases() {
    return Stream.of(
        // mesh flag, internalTokenEndpoint (issuer), expected isMeshRoute
        Arguments.of(Boolean.TRUE, ISSUER, true), // both signals -> mesh
        Arguments.of(Boolean.TRUE, null, true), // new config: mesh flag only
        Arguments.of(null, ISSUER, true), // legacy config: issuer only (fallback)
        Arguments.of(Boolean.FALSE, ISSUER, true), // explicit false but legacy issuer present
        Arguments.of(null, null, false), // real route: neither signal
        Arguments.of(Boolean.FALSE, null, false)); // explicit non-mesh
  }

  @ParameterizedTest
  @MethodSource("isMeshRouteCases")
  void isMeshRoute(Boolean mesh, String internalTokenEndpoint, boolean expected) {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setMesh(mesh);
    jc.setInternalTokenEndpoint(internalTokenEndpoint);

    // act
    boolean result = jc.isMeshRoute();

    // assert
    assertEquals(expected, result);
  }

  @Test
  void isMeshRoute_defaultsToFalse() {
    // arrange
    JumperConfig jc = new JumperConfig();

    // act & assert
    assertEquals(false, jc.isMeshRoute());
  }

  @Test
  void determineRealm_usesRealmHeader() {
    // arrange
    JumperConfig jc = new JumperConfig();
    ServerHttpRequest request = requestWithRealmHeader(Constants.DEFAULT_REALM);

    // act
    String realm = jc.determineRealm(request);

    // assert
    assertEquals(Constants.DEFAULT_REALM, realm);
  }

  @Test
  void determineRealm_usesLegacyIssuerRealmWhenHeaderIsMissing() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setRealmName(OTHER_REALM);
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/" + NON_DEFAULT_REALM);
    ServerHttpRequest request = requestWithoutRealmHeader();

    // act
    String realm = jc.determineRealm(request);

    // assert
    assertEquals(NON_DEFAULT_REALM, realm);
  }

  @Test
  void determineRealm_usesDefaultRealmWhenNoRealmSourceExists() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setRealmName(NON_DEFAULT_REALM);
    ServerHttpRequest request = requestWithoutRealmHeader();

    // act
    String realm = jc.determineRealm(request);

    // assert
    assertEquals(Constants.DEFAULT_REALM, realm);
  }

  private static ServerHttpRequest requestWithRealmHeader(String realm) {
    return MockServerHttpRequest.get("/").header(Constants.HEADER_REALM, realm).build();
  }

  private static ServerHttpRequest requestWithoutRealmHeader() {
    return MockServerHttpRequest.get("/").build();
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

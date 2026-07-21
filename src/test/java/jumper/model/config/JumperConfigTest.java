// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import jumper.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class JumperConfigTest {

  private static final String ISSUER = "http://localhost:1081/auth/realms/default";
  private static final String NON_DEFAULT_REALM = "sit";
  private static final String OTHER_REALM = "rv";
  private static final String CONSUMER = "some--consumer--app";

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

  @Test
  void getOauthCredentials_scopesOnlyConsumerEntryUsesDefaultAuthBundle() {
    // arrange
    JumperConfig jc = jumperConfigWithOauth(consumerScopesOnly(), fullDefault());

    // act
    Optional<OauthCredentials> result = jc.getOauthCredentials();

    // assert: consumer scope wins, authentication comes from the default entry as a whole
    assertTrue(result.isPresent());
    assertEquals("consumer-scope", result.get().getScopes());
    assertEquals("default-clientId", result.get().getClientId());
    assertEquals("default-clientSecret", result.get().getClientSecret());
    assertEquals("client_credentials", result.get().getGrantType());
  }

  @Test
  void getOauthCredentials_consumerEntryWithAuthConfigIsUsedAsIs() {
    // arrange: a consumer entry carrying authentication config is atomic — nothing is
    // completed from the default entry, not even blank fields
    OauthCredentials consumer = new OauthCredentials();
    consumer.setClientId("consumer-clientId");
    OauthCredentials def = fullDefault();
    def.setScopes("default-scope");
    JumperConfig jc = jumperConfigWithOauth(consumer, def);

    // act
    Optional<OauthCredentials> result = jc.getOauthCredentials();

    // assert
    assertTrue(result.isPresent());
    assertEquals("consumer-clientId", result.get().getClientId());
    assertNull(result.get().getClientSecret());
    assertNull(result.get().getGrantType());
    assertNull(result.get().getScopes());
  }

  @Test
  void getOauthCredentials_emptyConsumerEntryFallsBackToDefault() {
    // arrange
    JumperConfig jc = jumperConfigWithOauth(new OauthCredentials(), fullDefault());

    // act
    Optional<OauthCredentials> result = jc.getOauthCredentials();

    // assert
    assertTrue(result.isPresent());
    assertEquals("default-clientId", result.get().getClientId());
    assertEquals("client_credentials", result.get().getGrantType());
  }

  @Test
  void getOauthCredentials_consumerEntryWithoutDefaultIsUsedAsIs() {
    // arrange
    JumperConfig jc = jumperConfigWithOauth(consumerScopesOnly(), null);

    // act
    Optional<OauthCredentials> result = jc.getOauthCredentials();

    // assert
    assertTrue(result.isPresent());
    assertEquals("consumer-scope", result.get().getScopes());
    assertNull(result.get().getClientId());
  }

  @Test
  void getOauthCredentials_missingConsumerEntryFallsBackToDefault() {
    // arrange
    JumperConfig jc = jumperConfigWithOauth(null, fullDefault());

    // act
    Optional<OauthCredentials> result = jc.getOauthCredentials();

    // assert
    assertTrue(result.isPresent());
    assertEquals("default-clientId", result.get().getClientId());
  }

  @Test
  void getOauthCredentials_emptyWithoutOauthSection() {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);

    // act & assert
    assertTrue(jc.getOauthCredentials().isEmpty());
  }

  // Pins the OneToken scope-claim semantics: a consumer entry carrying auth config is used
  // as-is, so a missing scope stays missing — no scope claim, exactly as before the change.
  @Test
  void getSecurityScopes_consumerEntryWithAuthConfigDoesNotInheritDefaultScopes() {
    // arrange
    OauthCredentials consumer = new OauthCredentials();
    consumer.setClientId("consumer-clientId");
    OauthCredentials def = fullDefault();
    def.setScopes("default-scope");
    JumperConfig jc = jumperConfigWithOauth(consumer, def);

    // act & assert
    assertNull(jc.getSecurityScopes());
  }

  @Test
  void getSecurityScopes_scopesOnlyConsumerEntryYieldsConsumerScope() {
    // arrange
    OauthCredentials def = fullDefault();
    def.setScopes("default-scope");
    JumperConfig jc = jumperConfigWithOauth(consumerScopesOnly(), def);

    // act & assert
    assertEquals("consumer-scope", jc.getSecurityScopes());
  }

  private static JumperConfig jumperConfigWithOauth(
      OauthCredentials consumerEntry, OauthCredentials defaultEntry) {
    HashMap<String, OauthCredentials> oauth = new HashMap<>();
    if (consumerEntry != null) {
      oauth.put(CONSUMER, consumerEntry);
    }
    if (defaultEntry != null) {
      oauth.put(Constants.OAUTH_PROVIDER_KEY, defaultEntry);
    }
    JumperConfig jc = new JumperConfig();
    jc.setConsumer(CONSUMER);
    jc.setOauth(oauth);
    return jc;
  }

  private static OauthCredentials consumerScopesOnly() {
    OauthCredentials consumer = new OauthCredentials();
    consumer.setScopes("consumer-scope");
    return consumer;
  }

  private static OauthCredentials fullDefault() {
    OauthCredentials def = new OauthCredentials();
    def.setClientId("default-clientId");
    def.setClientSecret("default-clientSecret");
    def.setGrantType("client_credentials");
    return def;
  }

  private static ServerHttpRequest requestWithRealmHeader(String realm) {
    return MockServerHttpRequest.get("/").header(Constants.HEADER_REALM, realm).build();
  }

  private static ServerHttpRequest requestWithoutRealmHeader() {
    return MockServerHttpRequest.get("/").build();
  }
}

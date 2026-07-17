// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import jumper.Constants;
import jumper.util.AccessToken;
import jumper.util.ObjectMapperUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import tools.jackson.databind.json.JsonMapper;

class JumperConfigTest {

  @BeforeAll
  static void initObjectMapper() {
    // JumperConfig's base64 (de)serialization helpers and OauthTokenUtil run through
    // ObjectMapperUtil, which is normally populated by Spring. Populate the static holder for
    // this context-less unit test.
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

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

  /**
   * Guards the zone-failover path of DHEI-21196: {@code claims} deserialized from a routing_config
   * entry must survive {@link JumperConfig#fillProcessingInfo}, which re-sources other fields (e.g.
   * routeListener, gatewayClient) from the jumper_config header.
   */
  @Test
  void fillProcessingInfo_preservesClaimsFromRoutingConfigEntry() {
    // arrange: a routing_config list whose secondary entry carries an aud claim; the jumper_config
    // header ("e30=" = {}) deliberately has no claims, mirroring the CP contract
    JumperConfig secondaryEntry = new JumperConfig();
    secondaryEntry.setClaims(claimsMapOf("configured-audience"));
    secondaryEntry.setRemoteApiUrl("http://localhost:1080/provider");
    String routingConfig = JumperConfig.toJsonBase64(List.of(new JumperConfig(), secondaryEntry));

    ServerHttpRequest request =
        MockServerHttpRequest.get("/")
            .header(Constants.HEADER_AUTHORIZATION, "Bearer " + consumerToken())
            .header(Constants.HEADER_ROUTING_CONFIG, routingConfig)
            .header(Constants.HEADER_JUMPER_CONFIG, "e30=")
            .build();

    // act
    JumperConfig picked = JumperConfig.parseJumperConfigListFrom(request).get(1);
    picked.fillProcessingInfo(request);

    // assert
    assertEquals(1, picked.getConfiguredClaims().size());
    assertEquals("configured-audience", picked.getConfiguredClaims().get(0).getValue());
    assertEquals("eni--local-team--local-app", picked.getConsumer());
  }

  @Test
  void fillProcessingInfo_keepsRoutingConfigClaimsOverConflictingJumperConfigClaims() {
    // arrange: the jumper_config header carries its own, different claims - the selected
    // routing_config entry must still win (fillProcessingInfo re-sources routeListener and
    // gatewayClient from jumper_config, but never claims)
    JumperConfig secondaryEntry = new JumperConfig();
    secondaryEntry.setClaims(claimsMapOf("configured-audience"));
    secondaryEntry.setRemoteApiUrl("http://localhost:1080/provider");
    String routingConfig = JumperConfig.toJsonBase64(List.of(new JumperConfig(), secondaryEntry));

    JumperConfig headerJc = new JumperConfig();
    headerJc.setClaims(claimsMapOf("header-audience"));
    String jumperConfig = JumperConfig.toJsonBase64(headerJc);

    ServerHttpRequest request =
        MockServerHttpRequest.get("/")
            .header(Constants.HEADER_AUTHORIZATION, "Bearer " + consumerToken())
            .header(Constants.HEADER_ROUTING_CONFIG, routingConfig)
            .header(Constants.HEADER_JUMPER_CONFIG, jumperConfig)
            .build();

    // act
    JumperConfig picked = JumperConfig.parseJumperConfigListFrom(request).get(1);
    picked.fillProcessingInfo(request);

    // assert
    assertEquals(1, picked.getConfiguredClaims().size());
    assertEquals("configured-audience", picked.getConfiguredClaims().get(0).getValue());
  }

  @Test
  void getConfiguredClaims_returnsEmptyListWhenClaimsAbsent() {
    JumperConfig jc = new JumperConfig();

    assertEquals(List.of(), jc.getConfiguredClaims());
  }

  @Test
  void getConfiguredClaims_normalizesExplicitNullBucket() {
    // {"claims":{"default":null}} deserializes to a map entry with a null value
    JumperConfig jc = new JumperConfig();
    HashMap<String, List<Claim>> claims = new HashMap<>();
    claims.put(Constants.CLAIMS_DEFAULT_KEY, null);
    jc.setClaims(claims);

    assertEquals(List.of(), jc.getConfiguredClaims());
  }

  private static HashMap<String, List<Claim>> claimsMapOf(String audValue) {
    Claim audClaim = new Claim();
    audClaim.setKey(Constants.TOKEN_CLAIM_AUD);
    audClaim.setValue(audValue);
    HashMap<String, List<Claim>> claims = new HashMap<>();
    claims.put(Constants.CLAIMS_DEFAULT_KEY, List.of(audClaim));
    return claims;
  }

  private static String consumerToken() {
    return AccessToken.builder()
        .clientId("eni--local-team--local-app")
        .originZone("localZone")
        .originStargate("https://zone.local.de")
        .build()
        .getConsumerAccessToken();
  }

  private static ServerHttpRequest requestWithRealmHeader(String realm) {
    return MockServerHttpRequest.get("/").header(Constants.HEADER_REALM, realm).build();
  }

  private static ServerHttpRequest requestWithoutRealmHeader() {
    return MockServerHttpRequest.get("/").build();
  }
}

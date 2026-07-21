// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static jumper.config.Config.LOCAL_ISSUER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.JumperConfig.ConfiguredClaim;
import jumper.model.config.KeyInfo;
import jumper.util.AccessToken;
import jumper.util.OauthTokenUtil;
import jumper.util.ObjectMapperUtil;
import jumper.util.RsaUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link TokenGeneratorService}. These characterize publisher-token behavior and pin
 * the {@code aud}-claim semantics (single-audience override of {@code subscriberId}, {@code
 * subscriberId} fallback, and preservation of multiple audiences carried by the consumer token).
 */
class TokenGeneratorServiceTest {

  private static final String ISSUER = LOCAL_ISSUER;

  private KeyInfoService keyInfoService;
  private TokenGeneratorService tokenGeneratorService;

  @BeforeAll
  static void initObjectMapper() {
    // OauthTokenUtil#getAllClaimsFromToken parses the JWT header via ObjectMapperUtil, which is
    // normally populated by Spring. Populate the static holder for this context-less unit test.
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

  @BeforeEach
  void setUp() throws Exception {
    // arrange: a KeyInfoService that hands out the test RSA key + kid
    KeyInfo keyInfo = new KeyInfo();
    keyInfo.setPk(RsaUtils.getPrivateKey(Path.of("src/test/resources/keypair", "tls.key")));
    keyInfo.setKid("123456");

    keyInfoService = mock(KeyInfoService.class);
    when(keyInfoService.getKeyInfo()).thenReturn(keyInfo);

    tokenGeneratorService = new TokenGeneratorService(keyInfoService);
  }

  @Test
  @DisplayName("publisher token carries the expected static claims")
  void publisherToken_setsExpectedClaims() {
    // act
    String publisherToken =
        tokenGeneratorService.generateGatewayTokenForPublisher(ISSUER, "default");
    Claims claims = parse(publisherToken);

    // assert
    assertThat(claims.get("typ", String.class)).isEqualTo("Bearer");
    assertThat(claims.get("azp", String.class)).isEqualTo("stargate");
    assertThat(claims.get("clientId", String.class)).isEqualTo("gateway");
    assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    assertThat(claims.getExpiration()).isNotNull();
    assertThat(claims.getIssuedAt()).isNotNull();
  }

  @Test
  @DisplayName("consumer token audience overrides the subscriberId default")
  void singleConsumerAudience_overridesSubscriberId() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of("consumerAud"));
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(
            jc, "GET", ISSUER, "publisher-1", "subscriber-1");
    Claims claims = parse(providerLmsToken);

    // assert: consumer aud wins, subscriberId still present in its own claim
    assertThat(claims.getAudience()).containsExactly("consumerAud");
    assertThat(claims.get("subscriberId", String.class)).isEqualTo("subscriber-1");
    assertThat(claims.get("publisherId", String.class)).isEqualTo("publisher-1");
  }

  @Test
  @DisplayName("subscriberId is the audience fallback when the consumer token has none")
  void noConsumerAudience_fallsBackToSubscriberId() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of());
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, "subscriber-1");
    Claims claims = parse(providerLmsToken);

    // assert
    assertThat(claims.getAudience()).containsExactly("subscriber-1");
  }

  @Test
  @DisplayName("multiple consumer token audiences are all preserved in the provider LMS token")
  void multipleConsumerAudiences_allPreserved() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of("aud1", "aud2", "aud3"));
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, null);
    Claims claims = parse(providerLmsToken);

    // assert
    assertThat(claims.getAudience()).containsExactlyInAnyOrder("aud1", "aud2", "aud3");
  }

  @Test
  @DisplayName(
      "a single consumer token audience is emitted as a JSON string, not a one-element array")
  void singleConsumerAudience_emitsPlainStringOnTheWire() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of("consumerAud"));
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, null);

    // assert: inspect the raw JWT payload JSON directly. Claims#getAudience() normalizes both
    // wire forms (string or array) into a Set on read, so it cannot catch a regression here -
    // jjwt's ClaimsBuilder#audience().add(...) emits a JSON array even for one element; only
    // .single(...) collapses to a plain string, matching pre-migration wire format.
    JsonNode aud = rawPayloadJson(providerLmsToken).get("aud");
    assertThat(aud.isString()).isTrue();
    assertThat(aud.asString()).isEqualTo("consumerAud");
  }

  @Test
  @DisplayName("the subscriberId audience fallback is emitted as a JSON string")
  void subscriberIdFallback_emitsPlainStringOnTheWire() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of());
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, "subscriber-1");

    // assert
    JsonNode aud = rawPayloadJson(providerLmsToken).get("aud");
    assertThat(aud.isString()).isTrue();
    assertThat(aud.asString()).isEqualTo("subscriber-1");
  }

  @Test
  @DisplayName("multiple consumer token audiences are emitted as a JSON array")
  void multipleConsumerAudiences_emitJsonArrayOnTheWire() {
    // arrange
    String consumerToken = consumerTokenWithAudiences(List.of("aud1", "aud2"));
    JumperConfig jc = jumperConfig(consumerToken);

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, null);

    // assert
    JsonNode aud = rawPayloadJson(providerLmsToken).get("aud");
    assertThat(aud.isArray()).isTrue();
    assertThat(aud.size()).isEqualTo(2);
  }

  @Test
  @DisplayName("configured audience overrides incoming audience and subscriberId")
  void configuredAudience_hasHighestPrecedence() {
    // arrange
    JumperConfig jc = jumperConfig(consumerTokenWithAudiences(List.of("consumerAud")));
    setConfiguredAudience(jc, configuredAudience("provider-audience", null));

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, "subscriber-1");

    // assert
    assertThat(parse(providerLmsToken).getAudience()).containsExactly("provider-audience");
  }

  @Test
  @DisplayName("ConsumerClientId resolves the original consumer after a mesh hop")
  void consumerClientId_resolvesOriginalConsumerAfterMeshHop() {
    // arrange
    JumperConfig consumerZoneConfig =
        jumperConfig(consumerTokenWithAudiences(List.of("consumerAud")));
    String meshToken =
        tokenGeneratorService.generateMeshLmsToken(consumerZoneConfig, "GET", ISSUER);
    JumperConfig providerZoneConfig = jumperConfig(meshToken);
    setConfiguredAudience(
        providerZoneConfig,
        configuredAudience(null, Constants.CLAIM_VALUE_FROM_CONSUMER_CLIENT_ID));

    // act
    String providerLmsToken =
        tokenGeneratorService.generateProviderLmsToken(
            providerZoneConfig, "GET", ISSUER, null, null);

    // assert
    assertThat(parse(meshToken).get("azp", String.class)).isEqualTo("gateway");
    assertThat(parse(providerLmsToken).getAudience()).containsExactly("eni--local-team--local-app");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidAudienceConfigurations")
  void invalidConfiguredAudience_returnsInternalServerError(
      String description, ConfiguredClaim claim, String consumer, String expectedReason) {
    // arrange
    JumperConfig jc = jumperConfig(consumerTokenWithAudiences(List.of()));
    jc.setConsumer(consumer);
    setConfiguredAudience(jc, claim);

    // act
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> tokenGeneratorService.generateProviderLmsToken(jc, "GET", ISSUER, null, null));

    // assert
    assertThat(exception.getStatusCode().value()).isEqualTo(500);
    assertThat(exception.getReason())
        .isEqualTo("Invalid aud claim configuration: " + expectedReason);
  }

  @Test
  @DisplayName("createJwtTokenFromKey rejects an RS256 key weaker than 2048 bits with 401")
  void weakKey_isRejectedWith401() {
    // arrange
    Claims claims = Jwts.claims().subject("client").build();
    String weakKey = jumper.config.Config.PRIVATE_RSA_KEY_WEAK_EXAMPLE;

    // act
    ResponseStatusException ex = null;
    try {
      tokenGeneratorService.createJwtTokenFromKey(
          claims,
          "client",
          new java.util.Date(System.currentTimeMillis() + 60_000),
          new java.util.Date(),
          weakKey);
    } catch (ResponseStatusException e) {
      ex = e;
    }

    // assert
    assertThat(ex).isNotNull();
    assertThat(ex.getStatusCode().value()).isEqualTo(401);
    assertThat(ex.getReason()).contains("Key is too weak");
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private static String consumerTokenWithAudiences(List<String> audiences) {
    return AccessToken.builder()
        .clientId("eni--local-team--local-app")
        .originZone("localZone")
        .originStargate("https://zone.local.de")
        .audiences(audiences)
        .build()
        .getConsumerAccessToken();
  }

  private static JumperConfig jumperConfig(String authorizationToken) {
    JumperConfig jc = new JumperConfig();
    // production stores the raw Authorization header value, i.e. with the "Bearer " prefix
    jc.setAuthorizationToken("Bearer " + authorizationToken);
    jc.setRequestPath("/base/path");
    jc.setConsumer("eni--local-team--local-app");
    jc.setConsumerOriginZone("localZone");
    jc.setConsumerOriginStargate("https://zone.local.de");
    jc.setEnvName("localEnv");
    return jc;
  }

  private static void setConfiguredAudience(JumperConfig jc, ConfiguredClaim claim) {
    HashMap<String, List<ConfiguredClaim>> claims = new HashMap<>();
    claims.put(Constants.CLAIMS_DEFAULT_KEY, List.of(claim));
    jc.setClaims(claims);
  }

  private static ConfiguredClaim configuredAudience(String value, String valueFrom) {
    ConfiguredClaim claim = new ConfiguredClaim();
    claim.setKey(Constants.TOKEN_CLAIM_AUD);
    claim.setValue(value);
    claim.setValueFrom(valueFrom);
    return claim;
  }

  private static Stream<Arguments> invalidAudienceConfigurations() {
    return Stream.of(
        Arguments.of(
            "missing value and valueFrom",
            configuredAudience(null, null),
            "consumer",
            "exactly one of value or valueFrom must be set"),
        Arguments.of(
            "value and valueFrom both set",
            configuredAudience("audience", Constants.CLAIM_VALUE_FROM_CONSUMER_CLIENT_ID),
            "consumer",
            "exactly one of value or valueFrom must be set"),
        Arguments.of(
            "blank value", configuredAudience(" ", null), "consumer", "value must not be blank"),
        Arguments.of(
            "unsupported valueFrom",
            configuredAudience(null, "ProviderClientId"),
            "consumer",
            "unsupported valueFrom"),
        Arguments.of(
            "unresolved ConsumerClientId",
            configuredAudience(null, Constants.CLAIM_VALUE_FROM_CONSUMER_CLIENT_ID),
            null,
            "ConsumerClientId could not be resolved"));
  }

  private static Claims parse(String token) {
    Jwt<?, Claims> jwt = OauthTokenUtil.getAllClaimsFromToken("Bearer " + token);
    return jwt.getPayload();
  }

  /**
   * Decodes the raw JSON payload of a compact JWT, bypassing jjwt's {@link Claims} accessors
   * entirely. Needed to verify the actual {@code aud} wire representation (JSON string vs. array)
   * since {@link Claims#getAudience()} normalizes both forms into a {@code Set} on read and so
   * cannot distinguish them.
   */
  private static JsonNode rawPayloadJson(String jwt) {
    String[] parts = jwt.split("\\.");
    byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
    return JsonMapper.builder().build().readTree(payloadBytes);
  }
}

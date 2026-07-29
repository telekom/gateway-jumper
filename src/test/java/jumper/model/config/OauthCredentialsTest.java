// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers the two credential predicates. {@link OauthCredentials#canBuildTokenRequest()} has to stay
 * in sync with the request built by {@code
 * TokenFetchService#getAccessTokenWithOauthCredentialsObject}: it must be true exactly when that
 * method adds at least one authentication parameter.
 */
class OauthCredentialsTest {

  private static final String CLIENT_ID = "external_configured";
  private static final String PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";

  private static Stream<Arguments> credentialFields() {
    return Stream.of(
        Arguments.of(
            "clientId", (BiConsumer<OauthCredentials, String>) OauthCredentials::setClientId),
        Arguments.of(
            "clientSecret",
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setClientSecret),
        Arguments.of(
            "clientKey", (BiConsumer<OauthCredentials, String>) OauthCredentials::setClientKey),
        Arguments.of(
            "username", (BiConsumer<OauthCredentials, String>) OauthCredentials::setUsername),
        Arguments.of(
            "password", (BiConsumer<OauthCredentials, String>) OauthCredentials::setPassword),
        Arguments.of(
            "refreshToken",
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setRefreshToken));
  }

  private static Stream<Arguments> nonCredentialFields() {
    return Stream.of(
        Arguments.of("scopes", (BiConsumer<OauthCredentials, String>) OauthCredentials::setScopes),
        Arguments.of(
            "grantType", (BiConsumer<OauthCredentials, String>) OauthCredentials::setGrantType),
        Arguments.of(
            "tokenRequest",
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setTokenRequest));
  }

  @ParameterizedTest(name = "{0} counts as a credential field")
  @MethodSource("credentialFields")
  void hasAnyCredentialField_trueForCredentialField(
      String name, BiConsumer<OauthCredentials, String> setter) {
    // arrange
    OauthCredentials oc = new OauthCredentials();
    setter.accept(oc, "value");

    // act & assert
    assertTrue(oc.hasAnyCredentialField(), name + " should count as a credential field");
  }

  @ParameterizedTest(name = "{0} does not count as a credential field")
  @MethodSource("nonCredentialFields")
  void hasAnyCredentialField_falseForNonCredentialField(
      String name, BiConsumer<OauthCredentials, String> setter) {
    // arrange
    OauthCredentials oc = new OauthCredentials();
    setter.accept(oc, "value");

    // act & assert
    assertFalse(oc.hasAnyCredentialField(), name + " should not count as a credential field");
  }

  @ParameterizedTest(name = "blank clientId \"{0}\" is treated as absent")
  @ValueSource(strings = {"", " ", "\t"})
  void hasAnyCredentialField_blankIsAbsent(String blank) {
    // arrange
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(blank);

    // act & assert
    assertFalse(oc.hasAnyCredentialField());
  }

  @Test
  void hasAnyCredentialField_falseForEmptyEntry() {
    // arrange
    OauthCredentials oc = new OauthCredentials();

    // act & assert
    assertFalse(oc.hasAnyCredentialField());
  }

  @Test
  void copyWithScopes_copiesEveryFieldButScopes() {
    // arrange
    OauthCredentials source = new OauthCredentials();
    source.setClientId("clientId");
    source.setClientSecret("clientSecret");
    source.setClientKey("clientKey");
    source.setScopes("original_scope");
    source.setUsername("username");
    source.setPassword("password");
    source.setRefreshToken("refreshToken");
    source.setGrantType("client_credentials");
    source.setTokenRequest("BODY");

    // act
    OauthCredentials copy = source.copyWithScopes("narrowed_scope");

    // assert
    assertEquals("clientId", copy.getClientId());
    assertEquals("clientSecret", copy.getClientSecret());
    assertEquals("clientKey", copy.getClientKey());
    assertEquals("narrowed_scope", copy.getScopes());
    assertEquals("username", copy.getUsername());
    assertEquals("password", copy.getPassword());
    assertEquals("refreshToken", copy.getRefreshToken());
    assertEquals("client_credentials", copy.getGrantType());
    assertEquals("BODY", copy.getTokenRequest());
  }

  @Test
  void copyWithScopes_leavesSourceUntouched() {
    // arrange
    OauthCredentials source = new OauthCredentials();
    source.setClientId("clientId");
    source.setScopes("original_scope");

    // act
    OauthCredentials copy = source.copyWithScopes("narrowed_scope");

    // assert
    assertNotSame(source, copy);
    assertEquals("original_scope", source.getScopes());
    assertEquals("narrowed_scope", copy.getScopes());
  }

  @Test
  void canBuildTokenRequest_falseForEmptyEntry() {
    // arrange
    OauthCredentials oc = new OauthCredentials();

    // act & assert
    assertFalse(oc.canBuildTokenRequest());
  }

  @Test
  void canBuildTokenRequest_falseForNonCredentialFieldsOnly() {
    // arrange
    OauthCredentials oc = new OauthCredentials();
    oc.setGrantType("client_credentials");
    oc.setTokenRequest("HEADER");
    oc.setScopes("some_scope");

    // act & assert
    assertFalse(oc.canBuildTokenRequest());
  }

  @ParameterizedTest(name = "{0} is a complete mechanism")
  @MethodSource("completeMechanisms")
  void canBuildTokenRequest_trueForCompleteMechanism(String name, OauthCredentials oc) {
    // act & assert
    assertTrue(oc.canBuildTokenRequest(), name + " should be accepted");
  }

  private static Stream<Arguments> completeMechanisms() {
    return Stream.of(
        Arguments.of("clientId + clientSecret", credentials(CLIENT_ID, "secret", null, null, null)),
        Arguments.of("clientId + clientKey", credentials(CLIENT_ID, null, PRIVATE_KEY, null, null)),
        Arguments.of("username + password", credentials(null, null, null, "user", "pass")),
        Arguments.of("refreshToken", refreshTokenCredentials("refresh")));
  }

  @ParameterizedTest(name = "{0} is not a complete mechanism")
  @MethodSource("incompleteMechanisms")
  void canBuildTokenRequest_falseForIncompleteMechanism(String name, OauthCredentials oc) {
    // act & assert
    assertFalse(oc.canBuildTokenRequest(), name + " should be rejected");
  }

  private static Stream<Arguments> incompleteMechanisms() {
    return Stream.of(
        Arguments.of(
            "clientId without secret or key", credentials(CLIENT_ID, null, null, null, null)),
        Arguments.of(
            "clientSecret without clientId", credentials(null, "secret", null, null, null)),
        // The JWT client assertion uses clientId as iss, sub and client_id, so a key on its own
        // cannot identify the client.
        Arguments.of(
            "clientKey without clientId", credentials(null, null, PRIVATE_KEY, null, null)),
        Arguments.of("username without password", credentials(null, null, null, "user", null)),
        Arguments.of("password without username", credentials(null, null, null, null, "pass")));
  }

  @ParameterizedTest(name = "blank clientSecret \"{0}\" is treated as absent")
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  void canBuildTokenRequest_blankClientSecretIsAbsent(String blank) {
    // arrange
    OauthCredentials oc = credentials(CLIENT_ID, blank, null, null, null);

    // act & assert
    assertFalse(oc.canBuildTokenRequest());
  }

  @ParameterizedTest(name = "blank clientKey \"{0}\" is treated as absent")
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  void canBuildTokenRequest_blankClientKeyIsAbsent(String blank) {
    // arrange
    OauthCredentials oc = credentials(CLIENT_ID, null, blank, null, null);

    // act & assert
    assertFalse(oc.canBuildTokenRequest());
  }

  @ParameterizedTest(name = "blank refreshToken \"{0}\" is treated as absent")
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  void canBuildTokenRequest_blankRefreshTokenIsAbsent(String blank) {
    // arrange
    OauthCredentials oc = refreshTokenCredentials(blank);

    // act & assert
    assertFalse(oc.canBuildTokenRequest());
  }

  @ParameterizedTest(name = "{0} alone cannot build a token request unless it is the refreshToken")
  @MethodSource("credentialFields")
  void canBuildTokenRequest_singleCredentialFieldIsNotSufficient(
      String name, BiConsumer<OauthCredentials, String> setter) {
    // arrange
    OauthCredentials oc = new OauthCredentials();
    setter.accept(oc, "value");

    // act & assert
    // The refresh token is the only mechanism that authenticates on its own.
    assertEquals("refreshToken".equals(name), oc.canBuildTokenRequest(), name);
  }

  private static OauthCredentials credentials(
      String clientId, String clientSecret, String clientKey, String username, String password) {
    OauthCredentials oc = new OauthCredentials();
    oc.setClientId(clientId);
    oc.setClientSecret(clientSecret);
    oc.setClientKey(clientKey);
    oc.setUsername(username);
    oc.setPassword(password);
    return oc;
  }

  private static OauthCredentials refreshTokenCredentials(String refreshToken) {
    OauthCredentials oc = new OauthCredentials();
    oc.setRefreshToken(refreshToken);
    return oc;
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiConsumer;
import java.util.stream.Stream;
import jumper.model.config.OauthCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers {@link UpstreamOAuthFilter#canBuildTokenRequest(OauthCredentials)}, the guard that keeps
 * Jumper from sending a token request that carries no credentials at all.
 *
 * <p>The predicate has to stay in sync with the request built by {@code
 * TokenFetchService#getAccessTokenWithOauthCredentialsObject}: it must be true exactly when that
 * method adds at least one authentication parameter.
 */
class UpstreamOAuthFilterTest {

  private static final String CLIENT_ID = "external_configured";

  @Test
  @DisplayName("an entirely empty config cannot authenticate")
  void emptyConfigIsRejected() {
    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(new OauthCredentials())).isFalse();
  }

  @Test
  @DisplayName("grantType, tokenRequest and scopes alone do not authenticate")
  void nonCredentialFieldsAreNotEnough() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setGrantType("client_credentials");
    credentials.setTokenRequest("HEADER");
    credentials.setScopes("some_scope");

    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("completeMechanisms")
  @DisplayName("a complete authentication mechanism is accepted")
  void completeMechanismIsAccepted(String description, OauthCredentials credentials) {
    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isTrue();
  }

  static Stream<Arguments> completeMechanisms() {
    return Stream.of(
        Arguments.of("clientId + clientSecret", clientSecretCredentials()),
        Arguments.of("clientId + clientKey", clientKeyCredentials()),
        Arguments.of("username + password", usernamePasswordCredentials()),
        Arguments.of("refreshToken", refreshTokenCredentials()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("incompleteMechanisms")
  @DisplayName("half of a mechanism is not a mechanism")
  void incompleteMechanismIsRejected(String description, OauthCredentials credentials) {
    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isFalse();
  }

  static Stream<Arguments> incompleteMechanisms() {
    OauthCredentials clientIdOnly = new OauthCredentials();
    clientIdOnly.setClientId(CLIENT_ID);

    OauthCredentials secretWithoutId = new OauthCredentials();
    secretWithoutId.setClientSecret("secret");

    // The JWT client assertion uses clientId as iss, sub and client_id, so a key on its own
    // cannot identify the client.
    OauthCredentials keyWithoutId = new OauthCredentials();
    keyWithoutId.setClientKey("-----BEGIN PRIVATE KEY-----");

    OauthCredentials usernameOnly = new OauthCredentials();
    usernameOnly.setUsername("user");

    OauthCredentials passwordOnly = new OauthCredentials();
    passwordOnly.setPassword("pass");

    return Stream.of(
        Arguments.of("clientId without secret or key", clientIdOnly),
        Arguments.of("clientSecret without clientId", secretWithoutId),
        Arguments.of("clientKey without clientId", keyWithoutId),
        Arguments.of("username without password", usernameOnly),
        Arguments.of("password without username", passwordOnly));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("a blank client secret counts as absent")
  void blankClientSecretIsAbsent(String clientSecret) {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientSecret(clientSecret);

    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("a blank client key counts as absent")
  void blankClientKeyIsAbsent(String clientKey) {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientKey(clientKey);

    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isFalse();
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("a blank refresh token counts as absent")
  void blankRefreshTokenIsAbsent(String refreshToken) {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setRefreshToken(refreshToken);

    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("credentialFieldSetters")
  @DisplayName("a field that identifies but cannot authenticate is not sufficient on its own")
  void singleFieldIsNotSufficient(String field, BiConsumer<OauthCredentials, String> setter) {
    OauthCredentials credentials = new OauthCredentials();
    setter.accept(credentials, "value");

    boolean expected = "refreshToken".equals(field);
    assertThat(UpstreamOAuthFilter.canBuildTokenRequest(credentials)).isEqualTo(expected);
  }

  static Stream<Arguments> credentialFieldSetters() {
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
        // The only mechanism that is self-contained: a refresh token authenticates on its own.
        Arguments.of(
            "refreshToken",
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setRefreshToken));
  }

  private static OauthCredentials clientSecretCredentials() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientSecret("secret");
    return credentials;
  }

  private static OauthCredentials clientKeyCredentials() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(CLIENT_ID);
    credentials.setClientKey("-----BEGIN PRIVATE KEY-----");
    return credentials;
  }

  private static OauthCredentials usernamePasswordCredentials() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setUsername("user");
    credentials.setPassword("pass");
    return credentials;
  }

  private static OauthCredentials refreshTokenCredentials() {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setRefreshToken("refresh");
    return credentials;
  }
}

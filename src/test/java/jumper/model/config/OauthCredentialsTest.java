// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OauthCredentialsTest {

  static Stream<Arguments> authConfigFields() {
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
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setRefreshToken),
        Arguments.of(
            "grantType", (BiConsumer<OauthCredentials, String>) OauthCredentials::setGrantType),
        Arguments.of(
            "tokenRequest",
            (BiConsumer<OauthCredentials, String>) OauthCredentials::setTokenRequest));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("authConfigFields")
  void hasAuthConfig_trueForEveryAuthField(
      String field, BiConsumer<OauthCredentials, String> setField) {
    OauthCredentials credentials = new OauthCredentials();
    setField.accept(credentials, "value");

    assertTrue(credentials.hasAuthConfig());
  }

  @Test
  void hasAuthConfig_falseForScopesOnlyAndEmpty() {
    OauthCredentials empty = new OauthCredentials();
    assertFalse(empty.hasAuthConfig());

    OauthCredentials scopesOnly = new OauthCredentials();
    scopesOnly.setScopes("scope");
    assertFalse(scopesOnly.hasAuthConfig());

    OauthCredentials blanks = new OauthCredentials();
    blanks.setClientId("");
    blanks.setClientSecret("   ");
    blanks.setScopes("scope");
    assertFalse(blanks.hasAuthConfig());
  }

  @Test
  void withScopes_replacesScopesAndCopiesEverythingElse() {
    OauthCredentials original = fullCredentials("default");

    OauthCredentials copy = original.withScopes("consumer-scope");

    assertEquals("consumer-scope", copy.getScopes());
    assertEquals("default-clientId", copy.getClientId());
    assertEquals("default-clientSecret", copy.getClientSecret());
    assertEquals("default-clientKey", copy.getClientKey());
    assertEquals("default-username", copy.getUsername());
    assertEquals("default-password", copy.getPassword());
    assertEquals("default-refreshToken", copy.getRefreshToken());
    assertEquals("default-grantType", copy.getGrantType());
    assertEquals("default-tokenRequest", copy.getTokenRequest());
    // the original entry stays untouched
    assertEquals("default-scopes", original.getScopes());
  }

  private static OauthCredentials fullCredentials(String prefix) {
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(prefix + "-clientId");
    credentials.setClientSecret(prefix + "-clientSecret");
    credentials.setClientKey(prefix + "-clientKey");
    credentials.setScopes(prefix + "-scopes");
    credentials.setUsername(prefix + "-username");
    credentials.setPassword(prefix + "-password");
    credentials.setRefreshToken(prefix + "-refreshToken");
    credentials.setGrantType(prefix + "-grantType");
    credentials.setTokenRequest(prefix + "-tokenRequest");
    return credentials;
  }
}

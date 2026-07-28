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
import org.junit.jupiter.params.provider.ValueSource;

class OauthCredentialsTest {

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
}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import jumper.model.config.OauthCredentials;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UpstreamOAuthFilterTest {

  static Stream<Arguments> clientAuthCases() {
    return Stream.of(
        // clientId, clientSecret, clientKey, username, password, refreshToken, expected
        Arguments.of("id", "secret", null, null, null, null, true),
        Arguments.of("id", null, null, null, null, null, false),
        Arguments.of(null, "secret", null, null, null, null, false),
        Arguments.of(null, null, "key", null, null, null, true),
        Arguments.of("id", null, "key", null, null, null, true),
        Arguments.of(null, null, null, "user", "pass", null, true),
        Arguments.of(null, null, null, "user", null, null, false),
        Arguments.of(null, null, null, null, "pass", null, false),
        Arguments.of(null, null, null, null, null, "refresh", true),
        Arguments.of(null, null, null, null, null, null, false),
        Arguments.of("", "  ", "", "", "", "", false));
  }

  @ParameterizedTest
  @MethodSource("clientAuthCases")
  void hasResolvableClientAuth(
      String clientId,
      String clientSecret,
      String clientKey,
      String username,
      String password,
      String refreshToken,
      boolean expected) {
    // arrange
    OauthCredentials credentials = new OauthCredentials();
    credentials.setClientId(clientId);
    credentials.setClientSecret(clientSecret);
    credentials.setClientKey(clientKey);
    credentials.setUsername(username);
    credentials.setPassword(password);
    credentials.setRefreshToken(refreshToken);
    credentials.setGrantType("client_credentials");

    // act & assert
    assertEquals(expected, UpstreamOAuthFilter.hasResolvableClientAuth(credentials));
  }
}

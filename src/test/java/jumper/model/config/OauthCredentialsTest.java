// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class OauthCredentialsTest {

  @Test
  void withDefaults_consumerFieldsWin() {
    OauthCredentials consumer = fullCredentials("consumer");
    OauthCredentials base = fullCredentials("default");

    OauthCredentials merged = consumer.withDefaults(base);

    assertEquals("consumer-clientId", merged.getClientId());
    assertEquals("consumer-clientSecret", merged.getClientSecret());
    assertEquals("consumer-clientKey", merged.getClientKey());
    assertEquals("consumer-scopes", merged.getScopes());
    assertEquals("consumer-username", merged.getUsername());
    assertEquals("consumer-password", merged.getPassword());
    assertEquals("consumer-refreshToken", merged.getRefreshToken());
    assertEquals("consumer-grantType", merged.getGrantType());
    assertEquals("consumer-tokenRequest", merged.getTokenRequest());
  }

  @Test
  void withDefaults_missingFieldsInheritFromBase() {
    OauthCredentials consumer = new OauthCredentials();
    consumer.setScopes("consumer-scopes");
    OauthCredentials base = fullCredentials("default");

    OauthCredentials merged = consumer.withDefaults(base);

    assertEquals("default-clientId", merged.getClientId());
    assertEquals("default-clientSecret", merged.getClientSecret());
    assertEquals("default-clientKey", merged.getClientKey());
    assertEquals("default-username", merged.getUsername());
    assertEquals("default-password", merged.getPassword());
    assertEquals("default-refreshToken", merged.getRefreshToken());
    assertEquals("default-grantType", merged.getGrantType());
    assertEquals("default-tokenRequest", merged.getTokenRequest());
  }

  @Test
  void withDefaults_scopeIsReplacedNotCombined() {
    OauthCredentials consumer = new OauthCredentials();
    consumer.setScopes("consumer-scope");
    OauthCredentials base = new OauthCredentials();
    base.setScopes("default-scope another-default-scope");

    OauthCredentials merged = consumer.withDefaults(base);

    assertEquals("consumer-scope", merged.getScopes());
  }

  @Test
  void withDefaults_blankConsumerFieldInherits() {
    OauthCredentials consumer = new OauthCredentials();
    consumer.setClientId("");
    consumer.setClientSecret("   ");
    OauthCredentials base = fullCredentials("default");

    OauthCredentials merged = consumer.withDefaults(base);

    assertEquals("default-clientId", merged.getClientId());
    assertEquals("default-clientSecret", merged.getClientSecret());
  }

  @Test
  void withDefaults_nullBaseReturnsSameInstance() {
    OauthCredentials consumer = fullCredentials("consumer");

    assertSame(consumer, consumer.withDefaults(null));
  }

  @Test
  void withDefaults_fieldMissingInBothStaysNull() {
    OauthCredentials consumer = new OauthCredentials();
    consumer.setClientId("consumer-clientId");
    OauthCredentials base = new OauthCredentials();
    base.setClientSecret("default-clientSecret");

    OauthCredentials merged = consumer.withDefaults(base);

    assertEquals("consumer-clientId", merged.getClientId());
    assertEquals("default-clientSecret", merged.getClientSecret());
    assertNull(merged.getScopes());
    assertNull(merged.getGrantType());
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

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.unit.DataSize;

class OauthTokenFetchPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

  @Test
  void bindsConfiguredRefreshPolicy() {
    contextRunner
        .withPropertyValues(
            "jumper.oauth.token-fetch.refresh-ahead=45s",
            "jumper.oauth.token-fetch.min-serve=15s",
            "jumper.oauth.token-fetch.minimum-background-refresh-interval=7s",
            "jumper.oauth.token-fetch.connect-timeout=1500ms",
            "jumper.oauth.token-fetch.overall-timeout=6s",
            "jumper.oauth.token-fetch.request-wait-timeout=4s",
            "jumper.oauth.token-fetch.max-retries=1",
            "jumper.oauth.token-fetch.retry-backoff=250ms",
            "jumper.oauth.token-fetch.max-retry-backoff=900ms",
            "jumper.oauth.token-fetch.error-body-log-limit=4KB")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              OauthTokenFetchProperties properties =
                  context.getBean(OauthTokenFetchProperties.class);
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(1500));
              assertThat(properties.overallTimeout()).isEqualTo(Duration.ofSeconds(6));
              assertThat(properties.requestWaitTimeout()).isEqualTo(Duration.ofSeconds(4));
              assertThat(properties.maxRetries()).isOne();
              assertThat(properties.retryBackoff()).isEqualTo(Duration.ofMillis(250));
              assertThat(properties.maxRetryBackoff()).isEqualTo(Duration.ofMillis(900));
              assertThat(properties.errorBodyLogLimit()).isEqualTo(DataSize.ofKilobytes(4));
              assertThat(properties.refreshAhead()).isEqualTo(Duration.ofSeconds(45));
              assertThat(properties.minServe()).isEqualTo(Duration.ofSeconds(15));
              assertThat(properties.minimumBackgroundRefreshInterval())
                  .isEqualTo(Duration.ofSeconds(7));
            });
  }

  @Test
  void rejectsRefreshWindowAtMinimumServeThreshold() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.refresh-ahead=10s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("refreshAhead must exceed minServe"));
  }

  @Test
  void rejectsRequestWaitTimeoutAboveOverallTimeout() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.request-wait-timeout=6s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("requestWaitTimeout must not exceed overallTimeout"));
  }

  @Test
  void rejectsNonPositiveConnectTimeout() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.connect-timeout=0ms")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("connectTimeout"));
  }

  @Test
  void rejectsNegativeRetryCount() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.max-retries=-1")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("maxRetries"));
  }

  @Test
  void rejectsErrorBodyLogLimitAbove64Kilobytes() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.error-body-log-limit=65KB")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("errorBodyLogLimit must be between 1B and 64KB"));
  }

  @Test
  void accepts64KilobyteErrorBodyLogLimit() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.error-body-log-limit=64KB")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void rejectsZeroErrorBodyLogLimit() {
    contextRunner
        .withPropertyValues(validProperties())
        .withPropertyValues("jumper.oauth.token-fetch.error-body-log-limit=0B")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("errorBodyLogLimit must be between 1B and 64KB"));
  }

  @Test
  void legacyTtlOffsetRemainsFallbackForMinimumServe() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("jumper.tokencache.ttlOffset=17")
        .withUserConfiguration(PropertiesConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(OauthTokenFetchProperties.class).minServe())
                  .isEqualTo(Duration.ofSeconds(17));
            });
  }

  @Test
  void newMinimumServeSettingOverridesLegacyTtlOffset() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "jumper.tokencache.ttlOffset=17", "JUMPER_OAUTH_TOKEN_FETCH_MIN_SERVE=12s")
        .withUserConfiguration(PropertiesConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(OauthTokenFetchProperties.class).minServe())
                  .isEqualTo(Duration.ofSeconds(12));
            });
  }

  @Test
  void environmentVariablesOverrideApplicationDefaults() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            "testEnvironment",
                            Map.of(
                                "JUMPER_OAUTH_TOKEN_FETCH_CONNECT_TIMEOUT", "750ms",
                                "JUMPER_OAUTH_TOKEN_FETCH_MAX_RETRIES", "0",
                                "JUMPER_OAUTH_TOKEN_FETCH_REFRESH_AHEAD", "40s"))))
        .withUserConfiguration(PropertiesConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              OauthTokenFetchProperties properties =
                  context.getBean(OauthTokenFetchProperties.class);
              assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(750));
              assertThat(properties.maxRetries()).isZero();
              assertThat(properties.refreshAhead()).isEqualTo(Duration.ofSeconds(40));
            });
  }

  private String[] validProperties() {
    return new String[] {
      "jumper.oauth.token-fetch.connect-timeout=2s",
      "jumper.oauth.token-fetch.overall-timeout=5s",
      "jumper.oauth.token-fetch.request-wait-timeout=4s",
      "jumper.oauth.token-fetch.max-retries=1",
      "jumper.oauth.token-fetch.retry-backoff=200ms",
      "jumper.oauth.token-fetch.max-retry-backoff=1s",
      "jumper.oauth.token-fetch.error-body-log-limit=8KB",
      "jumper.oauth.token-fetch.refresh-ahead=30s",
      "jumper.oauth.token-fetch.min-serve=10s",
      "jumper.oauth.token-fetch.minimum-background-refresh-interval=5s"
    };
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OauthTokenFetchProperties.class)
  static class PropertiesConfiguration {}
}

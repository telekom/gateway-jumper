// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Resilience settings for fetching OAuth tokens from identity providers. Each component maps to a
 * {@code JUMPER_OAUTH_TOKEN_FETCH_*} environment variable documented in the README section "OAuth
 * Token Background Refresh"; keep both descriptions in sync.
 *
 * @param connectTimeout maximum time allowed to establish the token endpoint connection
 * @param overallTimeout maximum duration of one shared token fetch, including retries
 * @param requestWaitTimeout maximum time one request waits for a shared token fetch before it fails
 *     with 504; the fetch itself continues for other waiters and the cache
 * @param maxRetries maximum retries after a retryable connection failure
 * @param retryBackoff initial retry backoff
 * @param maxRetryBackoff maximum retry backoff
 * @param errorBodyLogLimit maximum identity provider error-body bytes retained for debug logging;
 *     the complete body is still drained
 * @param refreshAhead start refreshing this long before token expiry
 * @param minServe do not serve a cached token with this much lifetime or less remaining; a freshly
 *     fetched token that already violates this is forwarded but not cached
 * @param minimumBackgroundRefreshInterval minimum interval after a background refresh finishes
 *     before the same token key may be refreshed again
 */
@ConfigurationProperties("jumper.oauth.token-fetch")
@Validated
public record OauthTokenFetchProperties(
    @NotNull @DurationMin(millis = 1) @DurationUnit(ChronoUnit.MILLIS) Duration connectTimeout,
    @NotNull @DurationMin(millis = 1) @DurationUnit(ChronoUnit.MILLIS) Duration overallTimeout,
    @NotNull @DurationMin(millis = 1) @DurationUnit(ChronoUnit.MILLIS) Duration requestWaitTimeout,
    @Min(0) int maxRetries,
    @NotNull @DurationMin(millis = 1) @DurationUnit(ChronoUnit.MILLIS) Duration retryBackoff,
    @NotNull @DurationMin(millis = 1) @DurationUnit(ChronoUnit.MILLIS) Duration maxRetryBackoff,
    @NotNull DataSize errorBodyLogLimit,
    @NotNull @DurationMin(seconds = 1) @DurationUnit(ChronoUnit.SECONDS) Duration refreshAhead,
    @NotNull @DurationMin(seconds = 1) @DurationUnit(ChronoUnit.SECONDS) Duration minServe,
    @NotNull @DurationMin(seconds = 1) @DurationUnit(ChronoUnit.SECONDS)
        Duration minimumBackgroundRefreshInterval) {

  private static final long MAX_ERROR_BODY_LOG_BYTES = DataSize.ofKilobytes(64).toBytes();

  @AssertTrue(message = "refreshAhead must exceed minServe")
  public boolean isRefreshConfigurationValid() {
    if (refreshAhead == null || minServe == null) {
      return true;
    }

    return refreshAhead.compareTo(minServe) > 0;
  }

  @AssertTrue(message = "requestWaitTimeout must not exceed overallTimeout")
  public boolean isTimeoutConfigurationValid() {
    if (requestWaitTimeout == null || overallTimeout == null) {
      return true;
    }

    return requestWaitTimeout.compareTo(overallTimeout) <= 0;
  }

  @AssertTrue(message = "maxRetryBackoff must not be shorter than retryBackoff")
  public boolean isRetryConfigurationValid() {
    if (retryBackoff == null || maxRetryBackoff == null) {
      return true;
    }

    return maxRetryBackoff.compareTo(retryBackoff) >= 0;
  }

  @AssertTrue(message = "errorBodyLogLimit must be between 1B and 64KB")
  public boolean isErrorBodyLogLimitValid() {
    if (errorBodyLogLimit == null) {
      return true;
    }

    long bytes = errorBodyLogLimit.toBytes();
    return bytes > 0 && bytes <= MAX_ERROR_BODY_LOG_BYTES;
  }
}

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

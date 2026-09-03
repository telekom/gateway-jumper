// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class TokenFetchMetrics {

  private static final String FETCH_METRIC = "jumper.oauth.token.fetch";

  private final Map<Mode, Map<Outcome, Counter>> outcomeCounters = new EnumMap<>(Mode.class);
  private final Map<Mode, Counter> retryCounters = new EnumMap<>(Mode.class);
  private final AtomicInteger activeFetches = new AtomicInteger();
  private final AtomicInteger waiters = new AtomicInteger();

  public TokenFetchMetrics(MeterRegistry meterRegistry) {
    for (Mode mode : Mode.values()) {
      Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);
      for (Outcome outcome : Outcome.values()) {
        counters.put(
            outcome,
            meterRegistry.counter(
                FETCH_METRIC, "outcome", outcome.tagValue, "mode", mode.tagValue));
      }
      outcomeCounters.put(mode, counters);
      retryCounters.put(
          mode, meterRegistry.counter("jumper.oauth.token.retries", "mode", mode.tagValue));
    }
    meterRegistry.gauge("jumper.oauth.token.fetch.active", activeFetches);
    meterRegistry.gauge("jumper.oauth.token.waiters", waiters);
  }

  public void record(Outcome outcome, Mode mode) {
    outcomeCounters.get(mode).get(outcome).increment();
  }

  public void recordRetry(Mode mode) {
    retryCounters.get(mode).increment();
  }

  public void fetchStarted() {
    activeFetches.incrementAndGet();
  }

  public void fetchFinished() {
    activeFetches.decrementAndGet();
  }

  public void waiterStarted() {
    waiters.incrementAndGet();
  }

  public void waiterFinished() {
    waiters.decrementAndGet();
  }

  public enum Outcome {
    SUCCESS("success"),
    CACHE_HIT("cache_hit"),
    CONNECT_FAILURE("connect_failure"),
    DEADLINE("deadline"),
    RETRY_EXHAUSTED("retry_exhausted"),
    IDP_ERROR("idp_error"),
    BACKGROUND_REFRESH_FAILURE("background_refresh_failure"),
    REQUEST_BUILD_FAILURE("request_build_failure");

    private final String tagValue;

    Outcome(String tagValue) {
      this.tagValue = tagValue;
    }
  }

  public enum Mode {
    FOREGROUND("foreground"),
    BACKGROUND("background");

    private final String tagValue;

    Mode(String tagValue) {
      this.tagValue = tagValue;
    }
  }
}

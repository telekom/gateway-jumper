// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import jumper.config.WarmupProperties;
import jumper.health.WarmupHealthIndicator;
import org.junit.jupiter.api.Test;

class WarmupServiceTest {

  @Test
  void onApplicationReady_noUrls_skipsWarmupAndKeepsReadyState() {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(15));
    properties.setUrls(List.of());

    WarmupHealthIndicator healthIndicator = new WarmupHealthIndicator(properties);
    // With empty domains, the health indicator should already be ready
    assertThat(healthIndicator.isReady()).isTrue();

    WarmupService service = new WarmupService(properties, healthIndicator);
    // Should not throw, just log and return
    service.onApplicationReady();

    assertThat(healthIndicator.isReady()).isTrue();
  }

  @Test
  void onApplicationReady_withUrls_setsReadyAfterTimeout() throws InterruptedException {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(2));
    // Use a non-routable address to trigger connection timeout quickly
    properties.setUrls(List.of("https://192.0.2.1"));

    WarmupHealthIndicator healthIndicator = mock(WarmupHealthIndicator.class);

    WarmupService service = new WarmupService(properties, healthIndicator);
    service.onApplicationReady();

    // Wait for async warmup to complete (timeout + buffer)
    Thread.sleep(3000);

    // The health indicator should have been set to ready after warmup completes/times out
    verify(healthIndicator, atLeastOnce()).setReady();
  }

  @Test
  void healthIndicator_startsDown_withUrls() {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(15));
    properties.setUrls(List.of("https://example.com"));

    WarmupHealthIndicator healthIndicator = new WarmupHealthIndicator(properties);
    assertThat(healthIndicator.isReady()).isFalse();
    assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void healthIndicator_flipsUp_afterSetReady() {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(15));
    properties.setUrls(List.of("https://example.com"));

    WarmupHealthIndicator healthIndicator = new WarmupHealthIndicator(properties);
    assertThat(healthIndicator.isReady()).isFalse();

    healthIndicator.setReady();

    assertThat(healthIndicator.isReady()).isTrue();
    assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void healthIndicator_immediatelyReady_withEmptyUrls() {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(15));
    properties.setUrls(List.of());

    WarmupHealthIndicator healthIndicator = new WarmupHealthIndicator(properties);
    assertThat(healthIndicator.isReady()).isTrue();
    assertThat(healthIndicator.health().block().getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void healthIndicator_immediatelyReady_withNullUrls() {
    WarmupProperties properties = new WarmupProperties();
    properties.setEnabled(true);
    properties.setTimeout(Duration.ofSeconds(15));
    properties.setUrls(null);

    WarmupHealthIndicator healthIndicator = new WarmupHealthIndicator(properties);
    assertThat(healthIndicator.isReady()).isTrue();
  }
}

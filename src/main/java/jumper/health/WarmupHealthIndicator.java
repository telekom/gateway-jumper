// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.health;

import java.util.concurrent.atomic.AtomicBoolean;
import jumper.config.WarmupProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "jumper.warmup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WarmupHealthIndicator implements HealthIndicator {

  private final AtomicBoolean ready = new AtomicBoolean(false);

  public WarmupHealthIndicator(WarmupProperties warmupProperties) {
    // If no URLs configured, skip warmup and report ready immediately
    if (warmupProperties.getUrls() == null || warmupProperties.getUrls().isEmpty()) {
      ready.set(true);
    }
  }

  @Override
  public Health health() {
    if (ready.get()) {
      return Health.up().build();
    }
    return Health.down().withDetail("reason", "warmup in progress").build();
  }

  public void setReady() {
    ready.set(true);
  }

  public boolean isReady() {
    return ready.get();
  }
}

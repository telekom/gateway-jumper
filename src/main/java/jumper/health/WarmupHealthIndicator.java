// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.health;

import java.util.concurrent.atomic.AtomicBoolean;
import jumper.config.WarmupProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(
    prefix = "jumper.warmup",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class WarmupHealthIndicator implements ReactiveHealthIndicator {

  private final AtomicBoolean ready = new AtomicBoolean(false);

  public WarmupHealthIndicator(WarmupProperties warmupProperties) {
    // If no URLs configured, skip warmup and report ready immediately
    if (warmupProperties.getUrls() == null || warmupProperties.getUrls().isEmpty()) {
      ready.set(true);
    }
  }

  @Override
  public Mono<Health> health() {
    if (ready.get()) {
      return Mono.just(Health.up().build());
    }
    return Mono.just(Health.down().withDetail("reason", "warmup in progress").build());
  }

  public void setReady() {
    ready.set(true);
  }

  public boolean isReady() {
    return ready.get();
  }
}

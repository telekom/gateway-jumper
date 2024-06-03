// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import jumper.model.config.HealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ZoneHealthCheckService {

  @Value("${jumper.zone.health.defaultZoneHealth}")
  private Boolean defaultZoneHealth;

  private final Map<String, Boolean> zoneHealthCache = new ConcurrentHashMap<>();
  private final MeterRegistry meterRegistry;
  private final Map<String, AtomicInteger> atomicIntegerMap;

  public ZoneHealthCheckService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    this.atomicIntegerMap = new HashMap<>();
  }

  public Boolean getZoneHealth(String zone) {
    return zoneHealthCache.getOrDefault(zone, defaultZoneHealth);
  }

  public void setZoneHealth(String zone, Boolean health) {
    gaugeEvaluation(zone, health);
    zoneHealthCache.put(zone, health);
  }

  private void gaugeEvaluation(String zone, Boolean health) {
    if (atomicIntegerMap.containsKey(zone)) {
      AtomicInteger ai = atomicIntegerMap.get(zone);
      ai.set(health ? 1 : 0);
    } else {
      AtomicInteger ai = new AtomicInteger(health ? 1 : 0);
      Gauge.builder("zone.health.status", () -> ai)
          .description("Zone health status")
          .tag("zone", zone)
          .tag("status", HealthStatus.HEALTHY.toString())
          .register(meterRegistry);
      atomicIntegerMap.put(zone, ai);
    }
  }

  public void clearCache() {
    zoneHealthCache.clear();
  }
}

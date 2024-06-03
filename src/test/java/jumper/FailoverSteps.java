// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static jumper.config.Config.REMOTE_ZONE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import jumper.model.config.HealthStatus;
import jumper.service.ZoneHealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FailoverSteps {

  private final ZoneHealthCheckService zoneHealthCheckService;

  @Then("failover service for {} is returning {}")
  public void failoverServiceIsReturning(String zone, HealthStatus healthStatus) {
    boolean wantIsHealthy = healthStatus == HealthStatus.HEALTHY;
    boolean zoneStatus = zoneHealthCheckService.getZoneHealth(zone);

    assertEquals(
        wantIsHealthy,
        zoneStatus,
        "Want:" + healthStatus + "\n" + "Got: " + zoneHealthCheckService.getZoneHealth(zone));
  }

  @And("set zone state to unhealthy")
  public void setZoneUnhealthy() {
    zoneHealthCheckService.setZoneHealth(REMOTE_ZONE_NAME, false);
  }

  @After("@failover")
  public void clearCache() {
    zoneHealthCheckService.clearCache();
  }
}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "JUMPER_MANAGEMENT_PORT=0")
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class SeparateHealthProbeIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @Value("${local.management.port}")
  private int managementPort;

  @Test
  void healthProbeAliasesAreAvailableOnApplicationListener() {
    WebTestClient managementWebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();

    managementWebTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    managementWebTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    managementWebTestClient
        .get()
        .uri("/actuator/health/readiness")
        .exchange()
        .expectStatus()
        .isOk();
    managementWebTestClient.get().uri("/livez").exchange().expectStatus().isNotFound();
    managementWebTestClient.get().uri("/readyz").exchange().expectStatus().isNotFound();

    webTestClient.get().uri("/livez").exchange().expectStatus().isOk();
    webTestClient.get().uri("/readyz").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isNotFound();
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isNotFound();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isNotFound();
  }
}

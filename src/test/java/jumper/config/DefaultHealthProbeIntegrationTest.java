// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class DefaultHealthProbeIntegrationTest {

  @Autowired private WebTestClient webTestClient;

  @Test
  void healthEndpointAndProbeAliasesUseApplicationListener() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();
    webTestClient.get().uri("/livez").exchange().expectStatus().isOk();
    webTestClient.get().uri("/readyz").exchange().expectStatus().isOk();
  }
}

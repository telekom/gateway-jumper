// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.OauthTokenUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"jumper.warmup.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureObservability
@AutoConfigureWebTestClient(timeout = "PT10S")
class WarmupIntegrationTest {

  private static final int MOCK_UPSTREAM_PORT = 1090;

  static ClientAndServer mockUpstream;
  static MockServerClient mockClient;

  @Autowired WebTestClient webTestClient;

  @BeforeAll
  static void startMockUpstream() {
    mockUpstream = startClientAndServer(MOCK_UPSTREAM_PORT);
    mockClient = new MockServerClient("localhost", MOCK_UPSTREAM_PORT);
    mockClient.when(request()).respond(response().withStatusCode(200));
  }

  @AfterAll
  static void stopMockUpstream() {
    if (mockUpstream != null) {
      mockUpstream.stop();
    }
  }

  @Test
  void warmupRequestReachesUpstreamWithLmsToken() {
    String remoteApiUrl = "http://localhost:" + MOCK_UPSTREAM_PORT;

    // Build warmup jumper_config pointing at the mock upstream
    JumperConfig jc = new JumperConfig();
    jc.setRemoteApiUrl(remoteApiUrl);
    jc.setApiBasePath("/");
    jc.setRealmName(Constants.DEFAULT_REALM);
    jc.setEnvName("warmup");
    String jumperConfigBase64 = JumperConfig.toJsonBase64(jc);

    // Build a synthetic consumer token (same as WarmupService does)
    String consumerToken = jumper.util.TokenUtil.getConsumerAccessToken();

    // Send the warmup-style request through Jumper's filter chain
    // Includes remote_api_url legacy header required by fillWithLegacyHeaders
    webTestClient
        .get()
        .uri(Constants.PROXY_ROOT_PATH_PREFIX + "/warmup")
        .header(Constants.HEADER_JUMPER_CONFIG, jumperConfigBase64)
        .header(Constants.HEADER_AUTHORIZATION, "Bearer " + consumerToken)
        .header(Constants.HEADER_REMOTE_API_URL, remoteApiUrl)
        .header(Constants.HEADER_API_BASE_PATH, "/")
        .header(Constants.HEADER_REALM, Constants.DEFAULT_REALM)
        .header(Constants.HEADER_ENVIRONMENT, "warmup")
        .exchange()
        .expectStatus()
        .isOk();

    // Verify the mock upstream received the request
    HttpRequest[] recordedRequests = mockClient.retrieveRecordedRequests(request());
    assertThat(recordedRequests)
        .as("Mock upstream should have received the warmup request")
        .isNotEmpty();

    // Verify the upstream received a Bearer authorization header (LMS token, not the consumer
    // token)
    String authHeader = recordedRequests[0].getFirstHeader("Authorization");
    assertThat(authHeader).startsWith("Bearer ");

    // Parse the LMS token and verify it has expected claims
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(authHeader);

    assertThat(claimsFromToken.getBody().get("env", String.class)).isEqualTo("warmup");
    assertThat(claimsFromToken.getBody().get("typ", String.class)).isEqualTo("Bearer");
    assertThat(claimsFromToken.getBody().getIssuer()).contains("/default");
  }
}

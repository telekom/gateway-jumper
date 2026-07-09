// SPDX-FileCopyrightText: 2024 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.OauthTokenUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"jumper.warmup.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMetrics
@AutoConfigureTracing
@AutoConfigureWebTestClient(timeout = "PT10S")
class WarmupIntegrationTest {

  private static final int MOCK_UPSTREAM_PORT = 1090;

  static WireMockServer mockUpstream;

  @Autowired WebTestClient webTestClient;

  @BeforeAll
  static void startMockUpstream() {
    mockUpstream = new WireMockServer(options().port(MOCK_UPSTREAM_PORT));
    mockUpstream.start();
    mockUpstream.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
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
    // Includes legacy routing headers that remain valid as fallback input.
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
    List<LoggedRequest> recordedRequests = mockUpstream.findAll(anyRequestedFor(anyUrl()));
    assertThat(recordedRequests)
        .as("Mock upstream should have received the warmup request")
        .isNotEmpty();

    // Verify the upstream received a Bearer authorization header (LMS token, not the consumer
    // token)
    String authHeader = recordedRequests.get(0).getHeader("Authorization");
    assertThat(authHeader).startsWith("Bearer ");

    // Parse the LMS token and verify it has expected claims
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(authHeader);

    assertThat(claimsFromToken.getBody().get("env", String.class)).isEqualTo("warmup");
    assertThat(claimsFromToken.getBody().get("typ", String.class)).isEqualTo("Bearer");
    assertThat(claimsFromToken.getBody().getIssuer()).contains("/default");
  }
}

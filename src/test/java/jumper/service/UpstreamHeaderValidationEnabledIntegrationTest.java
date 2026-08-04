// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import jumper.Constants;
import jumper.mocks.RawHttpUpstream;
import jumper.model.config.JumperConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureWebTestClient(timeout = "PT10S")
class UpstreamHeaderValidationEnabledIntegrationTest {

  // Netty reads io.netty.handler.codec.http.rfc9112TransferEncoding once, when HttpObjectDecoder is
  // class-loaded during context refresh. Pin it to the strict default here (before the Spring
  // context and therefore Netty are initialised) so the test does not depend on the ambient
  // default — the same value production gets from jumper.http.rfc9112-transfer-encoding=true via
  // NettyRfc9112EnvironmentPostProcessor. Surefire runs this class in its own JVM
  // (reuseForks=false), so the property does not leak into other tests.
  static {
    System.setProperty("io.netty.handler.codec.http.rfc9112TransferEncoding", "true");
  }

  // WireMock/Jetty normalises outgoing framing headers and would silently drop the conflicting
  // Content-Length, so it can never emit a genuine Transfer-Encoding + Content-Length response.
  // RawHttpUpstream writes the malformed bytes verbatim so Netty's decoder actually sees them.
  static RawHttpUpstream upstream;

  @Autowired WebTestClient webTestClient;

  @BeforeAll
  static void startUpstream() throws IOException {
    upstream = RawHttpUpstream.servingConflictingFramingHeaders("{\"state\":\"ok\"}").start();
  }

  @AfterAll
  static void stopUpstream() throws IOException {
    System.clearProperty("io.netty.handler.codec.http.rfc9112TransferEncoding");
    if (upstream != null) {
      upstream.close();
    }
  }

  @Test
  void rejectsUpstreamResponseWithConflictingFramingHeaders() {
    String remoteApiUrl = upstream.baseUrl();

    JumperConfig jc = new JumperConfig();
    jc.setRemoteApiUrl(remoteApiUrl);
    jc.setApiBasePath("/");
    jc.setRealmName(Constants.DEFAULT_REALM);
    String jumperConfigBase64 = JumperConfig.toJsonBase64(jc);

    EntityExchangeResult<String> result =
        webTestClient
            .get()
            .uri(Constants.PROXY_ROOT_PATH_PREFIX + "/warmup")
            .header(Constants.HEADER_JUMPER_CONFIG, jumperConfigBase64)
            .header(
                Constants.HEADER_AUTHORIZATION,
                "Bearer " + jumper.util.TokenUtil.getConsumerAccessToken())
            .header(Constants.HEADER_REMOTE_API_URL, remoteApiUrl)
            .header(Constants.HEADER_API_BASE_PATH, "/")
            .header(Constants.HEADER_REALM, Constants.DEFAULT_REALM)
            .exchange()
            .expectBody(String.class)
            .returnResult();

    // Netty 4.2 defaults useRfc9112TransferEncoding=true: a response carrying both
    // Transfer-Encoding and Content-Length throws ContentLengthNotAllowedException in the response
    // decoder (RFC 9112 §6.1 request-smuggling defence). Jumper surfaces that as a 5xx.
    int status = result.getStatus().value();
    assertThat(status)
        .withFailMessage(
            "Expected a 5xx rejection of the malformed upstream framing, got %s", status)
        .isGreaterThanOrEqualTo(500);
  }
}

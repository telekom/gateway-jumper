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
class UpstreamHeaderValidationDisabledIntegrationTest {

  // Netty reads io.netty.handler.codec.http.rfc9112TransferEncoding once, when HttpObjectDecoder is
  // class-loaded during context refresh. Setting it here (in a static initialiser, before the
  // Spring context and therefore Netty are initialised) makes the decoder tolerate the conflicting
  // framing — the same effect production gets from jumper.http.rfc9112-transfer-encoding=false via
  // NettyRfc9112EnvironmentPostProcessor. Surefire runs this class in its own JVM
  // (reuseForks=false), so the property does not leak into other tests.
  static {
    System.setProperty("io.netty.handler.codec.http.rfc9112TransferEncoding", "false");
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
  void acceptUpstreamResponseWithConflictingFramingHeaders() {
    String remoteApiUrl = upstream.baseUrl();

    JumperConfig jc = new JumperConfig();
    jc.setRemoteApiUrl(remoteApiUrl);
    jc.setApiBasePath("/");
    jc.setRealmName(Constants.DEFAULT_REALM);
    jc.setEnvName("warmup");
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
            .header(Constants.HEADER_ENVIRONMENT, "warmup")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getResponseBody()).contains("state");
  }
}

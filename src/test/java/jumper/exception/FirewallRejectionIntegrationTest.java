// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import jumper.Constants;
import jumper.config.Config;
import jumper.util.TokenUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies that {@link LoggingServerExchangeRejectedHandler} is wired into the Spring Security
 * filter chain. The status code cannot prove it, since the default handler also answers {@code
 * 400}, so the WARN line doubles as the wiring assertion.
 *
 * <p>Both tests send the same request against an upstream stubbed to {@code 200}; only the stray
 * tab ({@code %09}) in front of the parameter name differs.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"jumper.warmup.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureTracing
@AutoConfigureWebTestClient(timeout = "PT10S")
class FirewallRejectionIntegrationTest {

  private static final String QUERY_PARAM = "u_ressource_type";
  private static final String QUERY_VALUE = "IPLSPortComp";
  private static final String REQUEST_PATH =
      Constants.PROXY_ROOT_PATH_PREFIX + Config.BASE_PATH + "/getItems";

  static WireMockServer mockUpstream;

  @Autowired WebTestClient webTestClient;

  @Autowired ServerExchangeRejectedHandler rejectedHandler;

  @Value("${local.server.port}")
  private int port;

  private ListAppender<ILoggingEvent> logAppender;
  private ch.qos.logback.classic.Logger logger;

  @BeforeAll
  static void startMockUpstream() {
    mockUpstream = new WireMockServer(options().dynamicPort());
    mockUpstream.start();
    mockUpstream.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
  }

  @AfterAll
  static void stopMockUpstream() {
    if (mockUpstream != null) {
      mockUpstream.stop();
    }
  }

  @BeforeEach
  void attachAppender() {
    mockUpstream.resetRequests();

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    logger = context.getLogger(LoggingServerExchangeRejectedHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  @DisplayName("the custom rejection handler is the one registered in the context")
  void customHandlerIsRegistered() {
    assertThat(rejectedHandler).isInstanceOf(LoggingServerExchangeRejectedHandler.class);
  }

  @Test
  @DisplayName("a tab encoded parameter name is rejected with 400 and logged at WARN")
  void tabInParameterName_isRejectedAndLogged() {
    // act: %09 decodes to a TAB, which StrictServerWebExchangeFirewall rejects
    webTestClient
        .get()
        .uri(rawUri(REQUEST_PATH + "?%09" + QUERY_PARAM + "=" + QUERY_VALUE))
        .headers(routingHeaders())
        .exchange()
        .expectStatus()
        .isBadRequest();

    // assert: the WARN line proves our handler ran, not the Spring Security default
    assertThat(logAppender.list).isNotEmpty();
    ILoggingEvent event = logAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("Request rejected by security firewall")
        .contains(QUERY_PARAM)
        .contains("\\u0009");
    assertThat(mockUpstream.findAll(anyRequestedFor(anyUrl()))).isEmpty();
  }

  @Test
  @DisplayName("the same request without the tab is proxied through to the upstream")
  void withoutTab_isProxiedSuccessfully() {
    // act
    webTestClient
        .get()
        .uri(rawUri(REQUEST_PATH + "?" + QUERY_PARAM + "=" + QUERY_VALUE))
        .headers(routingHeaders())
        .exchange()
        .expectStatus()
        .isOk();

    // assert
    List<LoggedRequest> recordedRequests = mockUpstream.findAll(anyRequestedFor(anyUrl()));
    assertThat(recordedRequests).hasSize(1);
    assertThat(recordedRequests.getFirst().queryParameter(QUERY_PARAM).firstValue())
        .isEqualTo(QUERY_VALUE);
    assertThat(logAppender.list).isEmpty();
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  /** Real route headers, pointed at the mock upstream's dynamically assigned port. */
  private static Consumer<HttpHeaders> routingHeaders() {
    return headers -> {
      headers.setBearerAuth(TokenUtil.getConsumerAccessToken());
      headers.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:" + mockUpstream.port());
      headers.set(Constants.HEADER_API_BASE_PATH, Config.BASE_PATH);
      headers.set(Constants.HEADER_ENVIRONMENT, Config.ENVIRONMENT);
      headers.set(Constants.HEADER_REALM, Config.REALM);
      headers.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
      headers.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  /**
   * Absolute URI so the percent-encoding survives verbatim: {@code uri(String)} would re-encode
   * {@code %09} into {@code %2509}, and {@code uri(URI)} bypasses the configured base URL.
   */
  private URI rawUri(String pathAndQuery) {
    return URI.create("http://localhost:" + port + pathAndQuery);
  }
}

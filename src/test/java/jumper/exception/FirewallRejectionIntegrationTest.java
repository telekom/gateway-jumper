// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies end to end that {@link LoggingServerExchangeRejectedHandler} is actually wired into the
 * Spring Security {@code WebFilterChainProxy}.
 *
 * <p>The status code alone cannot prove this, since Spring Security's default handler also answers
 * {@code 400}. The WARN log line is what distinguishes the two, so it doubles as the wiring
 * assertion.
 *
 * <p>The URL under test reproduces a real production request in which a stray tab ({@code %09})
 * prefixed a query parameter name.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"jumper.warmup.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureTracing
@AutoConfigureWebTestClient(timeout = "PT10S")
class FirewallRejectionIntegrationTest {

  @Autowired WebTestClient webTestClient;

  @Autowired ServerExchangeRejectedHandler rejectedHandler;

  // absolute URIs are required below, since WebTestClient#uri(URI) bypasses the configured base URL
  @Value("${local.server.port}")
  private int port;

  private ListAppender<ILoggingEvent> logAppender;
  private ch.qos.logback.classic.Logger logger;

  @BeforeEach
  void attachAppender() {
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
    // act: %09 decodes to a TAB, which is an ISO control character and therefore not an
    // allowed parameter name for StrictServerWebExchangeFirewall
    webTestClient
        .get()
        .uri(rawUri("/proxy/b2b-assura/ci-search/v1/getItems?%09u_ressource_type=IPLSPortComp"))
        .exchange()
        .expectStatus()
        .isBadRequest();

    // assert: the WARN line proves our handler ran, not the Spring Security default
    assertThat(logAppender.list).isNotEmpty();
    ILoggingEvent event = logAppender.list.getFirst();
    assertThat(event.getLevel()).isEqualTo(Level.WARN);
    assertThat(event.getFormattedMessage())
        .contains("Request rejected by security firewall")
        .contains("u_ressource_type")
        .contains("\\u0009");
  }

  @Test
  @DisplayName("the same URL without the tab is not rejected by the firewall")
  void withoutTab_isNotRejectedByFirewall() {
    // act: same request minus the control character; it fails later in the chain (no routing
    // headers), but must not be rejected by the firewall
    webTestClient
        .get()
        .uri(rawUri("/proxy/b2b-assura/ci-search/v1/getItems?u_ressource_type=IPLSPortComp"))
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(400));

    // assert
    assertThat(logAppender.list).isEmpty();
  }

  /**
   * Builds an absolute URI so the percent-encoding survives verbatim; {@code uri(String)} would
   * re-encode {@code %09} into {@code %2509}.
   */
  private URI rawUri(String pathAndQuery) {
    return URI.create("http://localhost:" + port + pathAndQuery);
  }
}

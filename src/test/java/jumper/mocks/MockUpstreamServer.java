// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static jumper.config.Config.REMOTE_HOST_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.List;
import jumper.util.OauthTokenUtil;

public class MockUpstreamServer {

  private WireMockServer server;
  final int upstreamLocalPort = REMOTE_HOST_PORT;

  public void startServer() {
    server =
        new WireMockServer(
            options()
                .port(upstreamLocalPort)
                .gzipDisabled(true)
                .extensions(new TestExpectationCallback()));
    server.start();
  }

  /**
   * Restart the upstream as a TLS server on the same port. WireMock cannot serve plain HTTP and
   * HTTPS on the same port, so the http listener is disabled and the https listener bound to the
   * upstream port the route points at.
   */
  public void secure() {
    server.stop();
    server =
        new WireMockServer(
            options()
                .httpDisabled(true)
                .httpsPort(upstreamLocalPort)
                .gzipDisabled(true)
                .extensions(new TestExpectationCallback()));
    server.start();
  }

  public void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  public void callbackRequest() {
    server.stubFor(
        any(urlPathEqualTo("/callback"))
            .withQueryParam("statusCode", matching("[0-9]+"))
            .willReturn(aResponse().withTransformers(TestExpectationCallback.NAME)));
  }

  public void failoverRequest(String path) {
    server.stubFor(
        any(urlPathEqualTo(path))
            .willReturn(aResponse().withTransformers(TestExpectationCallback.NAME)));
  }

  public void callbackRequestWithTimeout() {
    server.stubFor(
        any(urlPathEqualTo("/callback"))
            .willReturn(aResponse().withStatus(200).withFixedDelay(62_000)));
  }

  public void callbackRequestWithDropConnection() {
    server.stubFor(
        any(urlPathEqualTo("/callback")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
  }

  public void testEndpoint(String id, String path) {
    server.stubFor(any(urlPathEqualTo(path)).willReturn(aResponse().withStatus(200)));
  }

  public void verifyCount(String id, int count) {
    assertEquals(count, server.getAllServeEvents().size());
  }

  public void verifyTokenRequestPath(String expectedValue) {
    verifyTokenClaim("requestPath", expectedValue);
  }

  public void verifyTokenClaim(String claim, String expectedValue) {
    LoggedRequest request = firstRecordedRequest();
    String token = request.getHeader("Authorization");
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(token);
    assertEquals(expectedValue, claimsFromToken.getBody().get(claim, String.class));
  }

  public void verifyQueryParam(String expectedName, String expectedValue) {
    LoggedRequest request = firstRecordedRequest();
    String value = request.queryParameter(expectedName).firstValue();
    assertEquals(expectedValue, value);
  }

  private LoggedRequest firstRecordedRequest() {
    List<ServeEvent> events = server.getAllServeEvents();
    // WireMock returns serve events most-recent first; the oldest is the original request.
    return events.get(events.size() - 1).getRequest();
  }
}

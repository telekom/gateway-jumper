// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import java.util.concurrent.TimeUnit;
import jumper.service.OauthTokenUtil;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

public class MockUpstreamServer {

  private ClientAndServer mockServer;
  private MockServerClient mockServerClient;

  public void startServer() {
    int upstreamLocalPort = 1080;
    mockServer = startClientAndServer(upstreamLocalPort);
    String upstreamLocalHost = "localhost";
    mockServerClient = new MockServerClient(upstreamLocalHost, upstreamLocalPort);
  }

  public void stopServer() {
    mockServer.stop();
  }

  public void callbackRequest() {
    mockServerClient
        .when(
            request()
                .withPath("/callback")
                .withQueryStringParameters(param("statusCode", "[0-9]+")))
        .respond(callback().withCallbackClass("jumper.mocks.TestExpectationCallback"));
  }

  public void callbackRequestWithTimeout() {
    mockServerClient
        .when(request().withPath("/callback"))
        .error(HttpError.error().withDelay(TimeUnit.SECONDS, 62));
  }

  public void callbackRequestWithDropConnection() {
    mockServerClient
        .when(request().withPath("/callback"))
        .error(HttpError.error().withDropConnection(true));
  }

  public void testEndpoint(String id, String path) {
    mockServerClient
        .when(request().withPath(path), Times.exactly(1))
        .withId(id)
        .respond(response().withStatusCode(200).withHeaders(request().getHeaders()));
  }

  public void verifyCount(String id, int count) {
    mockServerClient.verify(id, VerificationTimes.exactly(count));
  }

  public void verifyTokenRequestPath(String expectedValue) {
    verifyTokenClaim("requestPath", expectedValue);
  }

  public void verifyTokenClaim(String claim, String expectedValue) {
    HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequests(request());

    String token = recordedRequests[0].getFirstHeader("Authorization");
    Jwt<Header, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));
    assertEquals(expectedValue, claimsFromToken.getBody().get(claim, String.class));
  }

  public void verifyQueryParam(String expectedName, String expectedValue) {
    HttpRequest[] recordedRequests = mockServerClient.retrieveRecordedRequests(request());

    String value = recordedRequests[0].getFirstQueryStringParameter(expectedName);

    assertEquals(expectedValue, value);
  }
}

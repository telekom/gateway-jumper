// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static jumper.config.Config.*;
import static jumper.util.JumperConfigUtil.addIdSuffix;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jumper.model.TokenInfo;
import jumper.util.ObjectMapperUtil;
import jumper.util.AccessToken;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpError;
import org.mockserver.model.RegexBody;
import org.springframework.http.HttpHeaders;

@Slf4j
public class MockIrisServer {

  private ClientAndServer mockServer;

  private final int irisLocalPort = 1081;

  private final String irisLocalHost = "localhost";

  private int responseCode = 200;

  public void startServer() {
    mockServer = startClientAndServer(irisLocalPort);
  }

  public void stopServer() {
    mockServer.stop();
  }

  public void createExpectationInternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_GATEWAY);
    List<Header> headersList = getHeaderList();

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withHeaders(headersList)
                .withMethod("POST")
                .withPath("/auth/realms/default/protocol/openid-connect/token")
                .withBody(
                    addIdSuffix("client_id=stargate", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenKeyed(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    RegexBody.regex(
                        ".*("
                            + addIdSuffix("client_id=external_configured", id)
                            + "|"
                            + "client_assertion=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9."
                            + "|"
                            + "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                            + "|"
                            + "grant_type=client_credentials"
                            + ")+")),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenScoped(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials&scope=scope_configured"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenHeaderClient(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_HEADER);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenHeaderScopedClient(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_HEADER);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials&scope=scope_header"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalBasicAuthCredentials(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody("grant_type=client_credentials")
                .withHeader(
                    "Authorization",
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenFromUsernamePassword(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withHeader(
                    "Authorization",
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes()))
                .withBody("username=username&password=geheim&grant_type=password"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalTokenFromUsernamePasswordOnly(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("username=username", id) + "&password=geheim&grant_type=password"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createExpectationExternalInvalidAuth(String id) {

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(401)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(
                    """
								{
								\t"error": "unauthorized_client",
								\t"error_description": "Invalid client or Invalid client credentials"
								}"""));
  }

  public void createExpectationDropConnection(String id) {

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/auth/realms/default/protocol/openid-connect/token")
                .withBody(
                    addIdSuffix("client_id=stargate", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(3))
        .error(HttpError.error().withDropConnection(true));
  }

  public void createExpectationExternalDropConnection(String id) {
    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(3))
        .error(HttpError.error().withDropConnection(true));
  }

  public void createExpectationWithTimeout(String id) {
    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 16));
  }

  public void createEmptyHeaderExpectationExternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withBody(tokenInfoJson)
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createEmptyBodyExpectationExternalToken(String id) {

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(
            response()
                .withStatusCode(responseCode)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store"))
                .withDelay(TimeUnit.SECONDS, 1));
  }

  public void createEmptyExpectationExternalToken(String id) {

    new MockServerClient(irisLocalHost, irisLocalPort)
        .when(
            request()
                .withMethod("POST")
                .withPath("/external")
                .withBody(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"),
            exactly(1))
        .respond(response().withStatusCode(responseCode).withDelay(TimeUnit.SECONDS, 1));
  }

  private List<Header> getHeaderList() {
    List<Header> headersList = new ArrayList<>();
    headersList.add(new Header(HttpHeaders.HOST, irisLocalHost + ":" + irisLocalPort));
    headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
    headersList.add(new Header(HttpHeaders.CONTENT_LENGTH, "86"));
    headersList.add(new Header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded"));
    return headersList;
  }

  private String getTokenInfoJson(String client) {
    TokenInfo tokenInfo = new TokenInfo();
    tokenInfo.setAccessToken(getToken(client));
    tokenInfo.setRefreshToken("asd");
    tokenInfo.setExpiresIn(300);
    tokenInfo.setRefreshExpiresIn(1800);
    tokenInfo.setTokenType("bearer");
    tokenInfo.setNotBeforePolicy(0);
    tokenInfo.setSessionState("69fc4e8-77e9-45f9-93e4-646a34f802cc");
    tokenInfo.setScope("profile email");

    String tokenInfoJson = null;
    try {
      tokenInfoJson = ObjectMapperUtil.getInstance().writeValueAsString(tokenInfo);
    } catch (JsonProcessingException e) {
      log.error(e.getMessage());
    }
    return tokenInfoJson;
  }

  private String getToken(String client) {
    AccessToken token =
        AccessToken.builder()
            .env(ENVIRONMENT_REMOTE)
            .clientId(client)
            .originZone(ORIGIN_ZONE_REMOTE)
            .originStargate(ORIGIN_STARGATE_REMOTE)
            .build();
    return token.getIdpToken();
  }

  public void setResponse(int response) {
    this.responseCode = response;
  }
}

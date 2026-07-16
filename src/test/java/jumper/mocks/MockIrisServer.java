// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static jumper.config.Config.*;
import static jumper.util.JumperConfigUtil.addIdSuffix;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import java.util.Base64;
import jumper.model.TokenInfo;
import jumper.util.AccessToken;
import jumper.util.ObjectMapperUtil;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Slf4j
public class MockIrisServer {

  private WireMockServer server;

  private final int irisLocalPort = 1081;

  private int responseCode = 200;

  public void startServer() {
    server = new WireMockServer(options().port(irisLocalPort).gzipDisabled(true));
    server.start();
  }

  public void stopServer() {
    if (server != null) {
      server.stop();
    }
  }

  public void createExpectationInternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_GATEWAY);

    server.stubFor(
        post(urlPathEqualTo("/auth/realms/default/protocol/openid-connect/token"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=stargate", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenKeyed(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                matching(
                    ".*("
                        + addIdSuffix("client_id=external_configured", id)
                        + "|client_assertion=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.|"
                        + "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
                        + "|grant_type=client_credentials)+"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenScoped(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials&scope=scope_configured"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenHeaderClient(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_HEADER);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenHeaderScopedClient(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_HEADER);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials&scope=scope_header"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalBasicAuthCredentials(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(equalTo("grant_type=client_credentials"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  /**
   * Basic-auth client credentials (from the provider "default" entry) combined with the
   * consumer-specific scope — the wire-level result of the consumer-over-default merge.
   */
  public void createExpectationExternalBasicAuthCredentialsScoped(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(equalTo("scope=scope_configured&grant_type=client_credentials"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenFromUsernamePassword(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())))
            .withRequestBody(equalTo("username=username&password=geheim&grant_type=password"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalTokenFromUsernamePasswordOnly(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("username=username", id) + "&password=geheim&grant_type=password"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(1000)));
  }

  public void createExpectationExternalInvalidAuth(String id) {

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_header", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(
                        """
                        {
                        \t"error": "unauthorized_client",
                        \t"error_description": "Invalid client or Invalid client credentials"
                        }\
                        """)));
  }

  public void createExpectationDropConnection(String id) {

    server.stubFor(
        post(urlPathEqualTo("/auth/realms/default/protocol/openid-connect/token"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=stargate", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
  }

  public void createExpectationExternalDropConnection(String id) {
    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
  }

  public void createExpectationWithTimeout(String id) {
    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(16_000)));
  }

  public void createEmptyHeaderExpectationExternalToken(String id) {

    String tokenInfoJson = getTokenInfoJson(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse().withStatus(responseCode).withBody(tokenInfoJson).withFixedDelay(1000)));
  }

  public void createEmptyBodyExpectationExternalToken(String id) {

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withFixedDelay(1000)));
  }

  public void createEmptyExpectationExternalToken(String id) {

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(
                equalTo(
                    addIdSuffix("client_id=external_configured", id)
                        + "&client_secret=secret&grant_type=client_credentials"))
            .willReturn(aResponse().withStatus(responseCode).withFixedDelay(1000)));
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
    } catch (JacksonException e) {
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

  public void createExpectationExternalTokenNoExpiresIn(String id) {
    String tokenInfoJson = getTokenInfoJsonWithoutExpiresIn(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(equalTo("grant_type=client_credentials"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(100)));
  }

  public void createExpectationExternalTokenNoExpiresInMultipleCalls(String id, int maxCalls) {
    String tokenInfoJson = getTokenInfoJsonWithoutExpiresIn(CONSUMER_EXTERNAL_CONFIGURED);

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(equalTo("grant_type=client_credentials"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("external_configured", id) + ":" + "secret")
                                    .getBytes())))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(100)));
  }

  public void createExpectationAlternativeTokenNoExpiresInMultipleCalls(String id, int maxCalls) {
    String tokenInfoJson = getTokenInfoJsonWithoutExpiresIn("alternative_client");

    server.stubFor(
        post(urlPathEqualTo("/external"))
            .withRequestBody(equalTo("grant_type=client_credentials"))
            .withHeader(
                "Authorization",
                equalTo(
                    "Basic "
                        + Base64.getEncoder()
                            .encodeToString(
                                (addIdSuffix("alternative_client", id) + ":" + "differentSecret")
                                    .getBytes())))
            .willReturn(
                aResponse()
                    .withStatus(responseCode)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")
                    .withBody(tokenInfoJson)
                    .withFixedDelay(100)));
  }

  public void verifyTokenEndpointCallCount(int expectedCount) {
    server.verify(exactly(expectedCount), postRequestedFor(urlPathEqualTo("/external")));
  }

  private String getTokenInfoJsonWithoutExpiresIn(String client) {
    // Build JSON manually to exclude expires_in field
    String accessToken = getToken(client);
    return String.format(
        "{\"access_token\":\"%s\",\"refresh_token\":\"asd\",\"token_type\":\"bearer\","
            + "\"not-before-policy\":0,\"session_state\":\"69fc4e8-77e9-45f9-93e4-646a34f802cc\","
            + "\"scope\":\"profile email\"}",
        accessToken);
  }
}

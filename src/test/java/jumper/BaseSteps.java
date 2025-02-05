// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static jumper.config.Config.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.function.Consumer;
import jumper.filter.RequestFilter;
import jumper.mocks.MockHorizonServer;
import jumper.mocks.MockIrisServer;
import jumper.mocks.MockUpstreamServer;
import jumper.model.config.Spectre;
import jumper.util.TokenUtil;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

@Getter
@Setter
public class BaseSteps {

  private static final Logger log = LoggerFactory.getLogger(BaseSteps.class);
  private MockUpstreamServer mockUpstreamServer;
  private MockIrisServer mockIrisServer;
  private MockHorizonServer mockHorizonServer;

  protected Consumer<HttpHeaders> httpHeadersOfRequest;

  protected String authHeader;
  private String responseStatusCode;
  private WebTestClient webTestClient;
  private WebTestClient.ResponseSpec requestExchange;
  private String id;

  @Autowired private RequestFilter rf;

  @Value("${jumper.stargate.url:https://stargate-integration.test.dhei.telekom.de}")
  private String stargateUrl;

  @Given("I need an upstream server with TLS")
  public void iNeedAnUpstreamServerWithTLS() {
    mockUpstreamServer.secure();
  }

  @And("API provider set to respond with a {int} status code")
  public void apiProviderWillRespondWithAStatusCode(int statusCode) {
    responseStatusCode = String.valueOf(statusCode);
  }

  @And("API provider set to respond on {word} path")
  public void apiProviderWillRespondWithAStatusCodeOnPath(String path_case) {
    switch (path_case) {
      case "real" -> mockUpstreamServer.failoverRequest(REMOTE_BASE_PATH);
      case "failover" -> mockUpstreamServer.failoverRequest(REMOTE_FAILOVER_BASE_PATH);
      case "provider" -> mockUpstreamServer.failoverRequest(REMOTE_PROVIDER_BASE_PATH);
    }
  }

  @And("Event provider set to respond with a {int} status code")
  public void eventProviderWillRespondWithAStatusCode(int statusCode) {
    responseStatusCode = String.valueOf(statusCode);
  }

  @And("API consumer receives a {int} status code")
  public void apisConsumerReceivesAStatusCode(int arg0) {
    requestExchange.expectStatus().isEqualTo(arg0);
  }

  @And("horizon receives a {int} status code")
  public void horizonReceivesAStatusCode(int arg0) {
    requestExchange.expectStatus().isEqualTo(arg0);
  }

  @And("error response contains msg {string} error {string} status {int}")
  public void assertErrorResponse(String msg, String error, int status) {
    requestExchange
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.method")
        .isEqualTo("GET")
        .jsonPath("$.service")
        .isEqualTo("jumper")
        .jsonPath("$.message")
        .isEqualTo(msg)
        .jsonPath("$.error")
        .isEqualTo(error)
        .jsonPath("$.status")
        .isEqualTo(status)
        .jsonPath("$.traceId")
        .exists()
        .jsonPath("$.tardisTraceId")
        .exists()
        .jsonPath("$.timestamp")
        .isNotEmpty();
  }

  @And("several realm fields are contained in the header")
  public void addCommaSeparatedRealmListToHeader() {
    setHttpHeadersOfRequest(
        httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.add(Constants.HEADER_REALM, "foo");
              httpHeaders.add(Constants.HEADER_REALM, "huhuhu");
              httpHeaders.add(Constants.HEADER_REALM, Constants.DEFAULT_REALM);
            }));
  }

  @And("horizon set to receive events")
  public void horizonReceiveEvents() {
    mockHorizonServer.createExpectationForEventsProduced(id);
  }

  @And("verify {int} horizon events received")
  public void horizonVerifySpectreCount(int count) {
    mockHorizonServer.createVerifyCount(id, count);
  }

  @And("verify received horizon events structure for method {word}")
  public void horizonVerifySpectreStructure(String method) {
    mockHorizonServer.createVerifyStructure(method, stargateUrl);
  }

  @And("verify received horizon events payload")
  public void horizonVerifySpectrePayload() {
    mockHorizonServer.createVerifyPayload();
  }

  @And("verify received horizon events payload to be base64 encoded")
  public void horizonVerifySpectrePayloadBase64() {
    mockHorizonServer.createVerifyPayloadBase64();
  }

  @And("verify adjusted horizon event")
  public void horizonVerifyAdjustedEvent() {
    mockHorizonServer.createVerifyEventType();
  }

  @And("IDP set to respond with {int} status code")
  public void idpSetToRespondWithStatusCode(int statusCode) {
    mockIrisServer.setResponse(statusCode);
  }

  @And("IDP is shut down")
  public void idpIsShutDown() {
    mockIrisServer.stopServer();
  }

  @And("IDP set to provide {word} token")
  public void idpWillRespondWithAStatusCode(String tokenType) {
    switch (tokenType) {
      case "internal":
        mockIrisServer.createExpectationInternalToken(id);
        break;
      case "external":
        mockIrisServer.createExpectationExternalToken(id);
        break;
      case "externalKey":
        mockIrisServer.createExpectationExternalTokenKeyed(id);
        break;
      case "externalScoped":
        mockIrisServer.createExpectationExternalTokenScoped(id);
        break;
      case "externalHeader":
        mockIrisServer.createExpectationExternalTokenHeaderClient(id);
        break;
      case "externalHeaderScoped":
        mockIrisServer.createExpectationExternalTokenHeaderScopedClient(id);
        break;
      case "externalBasicAuthCredentials":
        mockIrisServer.createExpectationExternalBasicAuthCredentials(id);
        break;
      case "externalUsernamePasswordCredentials":
        mockIrisServer.createExpectationExternalTokenFromUsernamePassword(id);
        break;
      case "externalUsernamePasswordCredentialsOnly":
        mockIrisServer.createExpectationExternalTokenFromUsernamePasswordOnly(id);
        break;
      case "externalInvalidAuth":
        mockIrisServer.createExpectationExternalInvalidAuth(id);
        break;
      default:
        fail("expected tokenType not configured");
    }
  }

  @And("external token IDP set to provide empty {word}")
  public void externalIdpWillWithEmptyResponse(String tokenType) {
    switch (tokenType) {
      case "body":
        mockIrisServer.createEmptyBodyExpectationExternalToken(id);
        break;
      case "header":
        mockIrisServer.createEmptyHeaderExpectationExternalToken(id);
        break;
      case "both":
        mockIrisServer.createEmptyExpectationExternalToken(id);
        break;
      default:
        fail("expected tokenType not configured");
    }
  }

  @And("IDP set to drop connection")
  public void idpSetToDropConnection() {
    mockIrisServer.createExpectationDropConnection(id);
  }

  @And("external token IDP request set to timeout")
  public void idpSetToTimeoutConnection() {
    mockIrisServer.createExpectationWithTimeout(id);
  }

  @And("external IDP set to drop connection")
  public void externalIDPSetToDropConnection() {
    mockIrisServer.createExpectationExternalDropConnection(id);
  }

  @When("consumer calls the proxy route")
  public void consumerCallsTheAPI() {
    mockUpstreamServer.callbackRequest();
    requestExchange =
        webTestClient
            .get()
            .uri("/proxy/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .exchange();
  }

  @When("consumer calls the proxy route with idp timeout")
  public void consumerCallsTheAPIWithIDPTimeout() {
    mockUpstreamServer.callbackRequest();
    var mutatedClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(60)).build();
    requestExchange =
        mutatedClient
            .get()
            .uri("/proxy/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .exchange();
  }

  @When("consumer calls the proxy route without base path")
  public void consumerCallsProxy() {
    requestExchange = webTestClient.get().uri("/proxy").headers(httpHeadersOfRequest).exchange();
  }

  @Then("metrics result does not contain {}")
  public void metricsDoesntContain(String word) {
    webTestClient
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .consumeWith(
            response -> {
              String body = response.getResponseBody();
              // log.info(body);
              if (body != null) {
                assertFalse(body.contains(word));
              }
            });
  }

  @When("consumer calls the proxy route and runs into timeout")
  public void consumerCallsTheAPIAndProviderRunsIntoTimeout() {
    mockUpstreamServer.callbackRequestWithTimeout();

    requestExchange =
        webTestClient
            .get()
            .uri("/proxy/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .exchange();
  }

  @When("consumer calls the proxy route and connection is dropped")
  public void consumerCallsTheAPIAndConnectionIsDropped() {
    mockUpstreamServer.callbackRequestWithDropConnection();

    requestExchange =
        webTestClient
            .get()
            .uri("/proxy/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .exchange();
  }

  @When("consumer calls the listener route")
  public void consumerCallsTheListenerRoute() {
    mockUpstreamServer.callbackRequest();

    requestExchange =
        webTestClient
            .get()
            .uri("/listener/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .exchange();
  }

  @When("consumer calls the listener route with JSON body")
  public void consumerCallsTheListenerRouteWithJsonBody() {
    mockUpstreamServer.callbackRequest();

    requestExchange =
        webTestClient
            .post()
            .uri("/listener/callback?statusCode=" + responseStatusCode)
            .headers(httpHeadersOfRequest)
            .body(BodyInserters.fromValue(getTestJson().toString()))
            .exchange();
  }

  @When("consumer calls the proxy route with {word}")
  public void consumerCallsTheAPIWith(String scenario) {
    String uri = "/proxy";

    switch (scenario) {
      case "remoteNoPathNoTrailingNo":
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "remoteNoPathNoTrailingYes":
        uri += "/";
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "remoteYesPathNoTrailingNo":
        setRemoteSuffix("/base");
        mockUpstreamServer.testEndpoint(id, "/base");
        break;
      case "remoteYesPathNoTrailingYes":
        setRemoteSuffix("/base");
        uri += "/";
        mockUpstreamServer.testEndpoint(id, "/base/");
        break;
      case "remoteYesPathYesTrailingNo":
        setRemoteSuffix("/base");
        uri += "/path";
        mockUpstreamServer.testEndpoint(id, "/base/path");
        break;
      case "remoteYesPathYesTrailingYes":
        setRemoteSuffix("/base");
        uri += "/path/";
        mockUpstreamServer.testEndpoint(id, "/base/path/");
        break;
      case "remoteTrailingPathYesTrailingNo":
        setRemoteSuffix("/base/");
        uri += "/path";
        mockUpstreamServer.testEndpoint(id, "/base/path");
        break;
      case "remoteTrailingPathYesTrailingYes":
        setRemoteSuffix("/base/");
        uri += "/path/";
        mockUpstreamServer.testEndpoint(id, "/base/path/");
        break;

      case "baseNoPathNoTrailingNo":
        setBasePathHeader("/");
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "baseNoPathNoTrailingYes":
        setBasePathHeader("/");
        uri += "/";
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "baseYesPathNoTrailingNo":
        setBasePathHeader("/base");
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "baseYesPathNoTrailingYes":
        setBasePathHeader("/base");
        uri += "/";
        mockUpstreamServer.testEndpoint(id, "/");
        break;
      case "baseYesPathYesTrailingNo":
        setBasePathHeader("/base");
        uri += "/path";
        mockUpstreamServer.testEndpoint(id, "/path");
        break;
      case "baseYesPathYesTrailingYes":
        setBasePathHeader("/base");
        uri += "/path/";
        mockUpstreamServer.testEndpoint(id, "/path/");
        break;
      case "encodedQueryParam":
        setBasePathHeader("/base");
        uri += "/path?validAt=2020-11-30T23%3A00%3A00%2B01%3A00&sig=123";
        mockUpstreamServer.testEndpoint(id, "/path");
        break;
      default:
        fail("scenario not defined");
    }

    requestExchange = webTestClient.get().uri(uri).headers(httpHeadersOfRequest).exchange();
  }

  @When("horizon calls the spectre route with {word}")
  public void horizonCallsTheSpectreRouteWithHead(String method) {
    mockHorizonServer.horizonCallback();
    setHttpHeadersOfRequest(TokenUtil.getEmptyHeaders());

    switch (method) {
      case "HEAD":
        requestExchange =
            webTestClient
                .head()
                .uri("/autoevent?statusCode=" + responseStatusCode)
                .headers(httpHeadersOfRequest)
                .exchange();
        break;
      case "POST":
        requestExchange =
            webTestClient
                .post()
                .uri("/autoevent?listener=spectre&statusCode=" + responseStatusCode)
                .body(BodyInserters.fromValue(getTestSpectre()))
                .headers(httpHeadersOfRequest)
                .exchange();
    }
  }

  @And("verify token requestPath value {word}")
  public void upstreamVerifyToken(String expected) {
    mockUpstreamServer.verifyTokenRequestPath(expected);
  }

  @And("verify query param {word} for value {word}")
  public void verifyQueryParam(String name, String value) {
    mockUpstreamServer.verifyQueryParam(name, value);
  }

  @And("current zone is {string}")
  public void currentZoneIs(String zone) {
    rf.setCurrentZone(zone);
  }

  public static JSONObject getTestJson() {

    JSONObject jsonObject = new JSONObject();
    try {
      jsonObject.put("swagger", "2.0");
      jsonObject.put(
          "info", new JSONObject().put("title", "spectre test").put("description", "tbd"));
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
    return jsonObject;
  }

  public static Spectre getTestSpectre() {
    return Spectre.builder().specversion("1.0").type("de.telekom.ei.listener").build();
  }

  private void setBasePathHeader(String basePathHeader) {
    this.setHttpHeadersOfRequest(
        httpHeadersOfRequest.andThen(
            httpHeaders -> httpHeaders.set(Constants.HEADER_API_BASE_PATH, basePathHeader)));
  }

  private void setRemoteSuffix(String suffix) {
    this.setHttpHeadersOfRequest(
        httpHeadersOfRequest.andThen(
            httpHeaders ->
                httpHeaders.set(
                    Constants.HEADER_REMOTE_API_URL,
                    httpHeaders.getFirst(Constants.HEADER_REMOTE_API_URL) + suffix)));
  }
}

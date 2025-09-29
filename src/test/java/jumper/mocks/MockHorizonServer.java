// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static jumper.BaseSteps.getTestJson;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import jumper.Constants;
import jumper.config.Config;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreKind;
import jumper.util.ObjectMapperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpHeaders;

@RequiredArgsConstructor
@Slf4j
public class MockHorizonServer {

  private static final int horizonLocalPort = 1082;
  private static final ClientAndServer mockServerClient = startClientAndServer(horizonLocalPort);

  public void resetMockServer() {
    mockServerClient.reset();
  }

  public void createExpectationForEventsProduced(String id) {
    mockServerClient
        .when(request().withHeaders(getHeaderList(id)).withMethod("POST").withPath("/v1/events"))
        .withId(id)
        .respond(
            response()
                .withStatusCode(201)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store")));
  }

  public void createVerifyCount(String id, int count) {
    await()
        .atMost(Duration.ofSeconds(5))
        .with()
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(() -> mockServerClient.verify(id, VerificationTimes.exactly(count)));
  }

  public void createVerifyStructure(String method, String stargateUrl) {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seRequestString = recordedRequests[0].getBodyAsString();
    String seResponseString = recordedRequests[1].getBodyAsString();

    ObjectMapper om =
        ObjectMapperUtil.getInstance()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      assertSpectreEvent(om.readValue(seRequestString, Spectre.class), method, true, stargateUrl);
      assertSpectreEvent(om.readValue(seResponseString, Spectre.class), method, false, stargateUrl);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyPayload() {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seRequestString = recordedRequests[0].getBodyAsString();
    String seResponseString = recordedRequests[1].getBodyAsString();

    try {
      Object expected =
          ObjectMapperUtil.getInstance().readValue(getTestJson().toString(), LinkedHashMap.class);
      assertEquals(
          expected,
          ObjectMapperUtil.getInstance()
              .readValue(seRequestString, Spectre.class)
              .getData()
              .getPayload());
      assertEquals(
          expected,
          ObjectMapperUtil.getInstance()
              .readValue(seResponseString, Spectre.class)
              .getData()
              .getPayload());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyPayloadBase64() {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seRequestString = recordedRequests[0].getBodyAsString();
    String seResponseString = recordedRequests[1].getBodyAsString();

    ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    Pattern BASE64_PATTERN =
        Pattern.compile("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$");

    try {
      assertTrue(
          BASE64_PATTERN
              .matcher(
                  String.valueOf(
                      om.readValue(seRequestString, Spectre.class).getData().getPayload()))
              .matches());
      assertTrue(
          BASE64_PATTERN
              .matcher(
                  String.valueOf(
                      om.readValue(seResponseString, Spectre.class).getData().getPayload()))
              .matches());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyEventType() {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seEventString = recordedRequests[0].getBodyAsString();

    try {
      assertEquals(
          "de.telekom.ei.listener.spectre",
          ObjectMapperUtil.getInstance().readValue(seEventString, Spectre.class).getType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void horizonCallback() {
    mockServerClient
        .when(
            request()
                .withPath("/v1/events")
                .withQueryStringParameters(param("statusCode", "[0-9]+")))
        .respond(callback().withCallbackClass("jumper.mocks.TestExpectationCallback"));
  }

  private List<Header> getHeaderList(String id) {
    List<Header> headersList = new ArrayList<>();
    headersList.add(new Header(HttpHeaders.HOST, "localhost:" + horizonLocalPort));
    headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
    headersList.add(new Header(HttpHeaders.ACCEPT_ENCODING, "gzip"));
    headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, id));

    return headersList;
  }

  private void assertSpectreEvent(Spectre s, String method, boolean request, String stargateUrl) {
    assertEquals("1.0", s.getSpecversion());
    assertEquals("de.telekom.ei.listener", s.getType());
    assertEquals(stargateUrl, s.getSource());
    assertNotNull(s.getId());
    assertEquals("application/json", s.getDatacontenttype());
    assertEquals(Config.CONSUMER, s.getData().getConsumer());
    assertEquals(Config.LISTENER_PROVIDER, s.getData().getProvider());
    assertEquals(Config.LISTENER_ISSUE, s.getData().getIssue());
    assertEquals(method, s.getData().getMethod());
    assertEquals(Config.ORIGIN_ZONE, s.getData().getHeader().get("X-Origin-Zone"));
    assertEquals(Config.ORIGIN_STARGATE, s.getData().getHeader().get("X-Origin-Stargate"));
    assertEquals("zone.local.de", s.getData().getHeader().get("X-Forwarded-Host"));
    assertEquals("443", s.getData().getHeader().get("X-Forwarded-Port"));
    assertNotNull(s.getData().getHeader().get("Authorization"));
    assertEquals(Config.ENVIRONMENT, s.getData().getHeader().get("environment"));
    assertEquals("default", s.getData().getHeader().get("realm"));
    if (request) {
      assertEquals(SpectreKind.REQUEST.name(), s.getData().getKind());
    } else {
      assertEquals(SpectreKind.RESPONSE.name(), s.getData().getKind());
      assertEquals(200, s.getData().getStatus());
    }
  }
}

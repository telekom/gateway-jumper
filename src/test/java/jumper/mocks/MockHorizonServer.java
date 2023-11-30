package jumper.mocks;

import static jumper.BaseSteps.getTestJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import jumper.BaseSteps;
import jumper.Constants;
import jumper.config.Config;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreKind;
import lombok.RequiredArgsConstructor;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;
import org.springframework.http.HttpHeaders;

@RequiredArgsConstructor
public class MockHorizonServer {

  private ClientAndServer mockServer;
  private MockServerClient mockServerClient;

  private final BaseSteps baseSteps;

  private final int horizonLocalPort = 1082;

  private final String horizonLocalHost = "localhost";

  public void startServer() {
    mockServer = startClientAndServer(horizonLocalPort);
    mockServerClient = new MockServerClient(horizonLocalHost, horizonLocalPort);
  }

  public void stopServer() {
    mockServer.stop();
  }

  public void createExpectation2Events(String id) {
    mockServerClient
        .when(
            request().withHeaders(getHeaderList(id)).withMethod("POST").withPath("/v1/events"),
            exactly(2))
        .withId(id)
        .respond(
            response()
                .withStatusCode(201)
                .withHeaders(
                    new Header("Content-Type", "application/json; charset=utf-8"),
                    new Header("Cache-Control", "no-store")));
  }

  public void createVerifyCount(String id, int count) {
    mockServerClient.verify(id, VerificationTimes.exactly(count));
  }

  public void createVerifyStructure(String method) {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seRequestString = recordedRequests[0].getBodyAsString();
    String seResponseString = recordedRequests[1].getBodyAsString();

    ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      assertSpectreEvent(om.readValue(seRequestString, Spectre.class), method, true);
      assertSpectreEvent(om.readValue(seResponseString, Spectre.class), method, false);
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

    ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      Object expected = om.readValue(getTestJson().toString(), LinkedHashMap.class);
      assertEquals(expected, om.readValue(seRequestString, Spectre.class).getData().getPayload());
      assertEquals(expected, om.readValue(seResponseString, Spectre.class).getData().getPayload());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyEventType() {
    HttpRequest[] recordedRequests =
        mockServerClient.retrieveRecordedRequests(
            request().withMethod("POST").withPath("/v1/events"));

    String seEventString = recordedRequests[0].getBodyAsString();

    ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    try {
      assertEquals(
          "de.telekom.ei.listener.spectre", om.readValue(seEventString, Spectre.class).getType());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public void horizonCallback() {
    new MockServerClient(horizonLocalHost, horizonLocalPort)
        .when(
            request()
                .withPath("/v1/events")
                .withQueryStringParameters(param("statusCode", "[0-9]+")))
        .respond(callback().withCallbackClass("jumper.mocks.TestExpectationCallback"));
  }

  private List<Header> getHeaderList(String id) {
    List<Header> headersList = new ArrayList<>();
    headersList.add(new Header(HttpHeaders.USER_AGENT, "ReactorNetty/1.0.38"));
    headersList.add(new Header(HttpHeaders.HOST, horizonLocalHost + ":" + horizonLocalPort));
    headersList.add(new Header(HttpHeaders.ACCEPT, "*/*"));
    headersList.add(new Header(HttpHeaders.ACCEPT_ENCODING, "gzip"));
    headersList.add(new Header(HttpHeaders.CONTENT_TYPE, "application/json"));
    headersList.add(new Header(Constants.HEADER_X_B3_TRACE_ID, id));

    return headersList;
  }

  private void assertSpectreEvent(Spectre s, String method, boolean request) {
    assertEquals("1.0", s.getSpecversion());
    assertEquals("de.telekom.ei.listener", s.getType());
    assertEquals(baseSteps.getStargateUrl(), s.getSource());
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

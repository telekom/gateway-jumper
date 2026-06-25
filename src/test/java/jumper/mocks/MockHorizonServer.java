// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static jumper.BaseSteps.getTestJson;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Slf4j
public class MockHorizonServer {

  private static final int horizonLocalPort = 1082;
  private static final WireMockServer server =
      new WireMockServer(
          options()
              .port(horizonLocalPort)
              .gzipDisabled(true)
              .extensions(new TestExpectationCallback()));

  static {
    server.start();
  }

  public void resetMockServer() {
    server.resetAll();
  }

  public void createExpectationForEventsProduced(String id) {
    server.stubFor(
        post(urlPathEqualTo("/v1/events"))
            .withHeader(Constants.HEADER_X_B3_TRACE_ID, equalTo(id))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json; charset=utf-8")
                    .withHeader("Cache-Control", "no-store")));
  }

  public void createVerifyCount(String id, int count) {
    await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .with()
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () ->
                server.verify(
                    exactly(count),
                    postRequestedFor(urlPathEqualTo("/v1/events"))
                        .withHeader(Constants.HEADER_X_B3_TRACE_ID, equalTo(id))));
  }

  private List<LoggedRequest> retrieveEventsForTrace(String id, int minCount) {
    return retrieveEvents(
        postRequestedFor(urlPathEqualTo("/v1/events"))
            .withHeader(Constants.HEADER_X_B3_TRACE_ID, equalTo(id)),
        minCount);
  }

  /**
   * Auto-generated (adjusted) events are emitted with jumper's own trace id, which differs from the
   * scenario's {@code id}. The {@code @After("@horizon")} hook resets the server between scenarios,
   * so retrieving all recorded events without a trace-id filter is safe here.
   */
  private List<LoggedRequest> retrieveAllEvents(int minCount) {
    return retrieveEvents(postRequestedFor(urlPathEqualTo("/v1/events")), minCount);
  }

  private List<LoggedRequest> retrieveEvents(
      com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern, int minCount) {
    java.util.concurrent.atomic.AtomicReference<List<LoggedRequest>> ref =
        new java.util.concurrent.atomic.AtomicReference<>(List.of());
    await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .with()
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(
            () -> {
              List<LoggedRequest> recorded = server.findAll(pattern);
              assertTrue(
                  recorded.size() >= minCount,
                  "expected at least " + minCount + " recorded events but got " + recorded.size());
              ref.set(recorded);
            });
    return ref.get();
  }

  public void createVerifyStructure(String id, String method, String stargateUrl) {
    List<LoggedRequest> recordedRequests = retrieveEventsForTrace(id, 2);

    String seRequestString = bodyOf(recordedRequests.get(0));
    String seResponseString = bodyOf(recordedRequests.get(1));

    ObjectMapper om = ObjectMapperUtil.getInstance();

    try {
      assertSpectreEvent(om.readValue(seRequestString, Spectre.class), method, true, stargateUrl);
      assertSpectreEvent(om.readValue(seResponseString, Spectre.class), method, false, stargateUrl);
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyPayload(String id) {
    List<LoggedRequest> recordedRequests = retrieveEventsForTrace(id, 2);

    String seRequestString = bodyOf(recordedRequests.get(0));
    String seResponseString = bodyOf(recordedRequests.get(1));

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
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyPayloadBase64(String id) {
    List<LoggedRequest> recordedRequests = retrieveEventsForTrace(id, 2);

    String seRequestString = bodyOf(recordedRequests.get(0));
    String seResponseString = bodyOf(recordedRequests.get(1));

    ObjectMapper om = ObjectMapperUtil.getInstance();

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
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public void createVerifyEventType(String id) {
    List<LoggedRequest> recordedRequests = retrieveAllEvents(1);

    String seEventString = bodyOf(recordedRequests.get(0));

    try {
      assertEquals(
          "de.telekom.ei.listener.spectre",
          ObjectMapperUtil.getInstance().readValue(seEventString, Spectre.class).getType());
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  public void horizonCallback() {
    server.stubFor(
        any(urlPathEqualTo("/v1/events"))
            .withQueryParam("statusCode", matching("[0-9]+"))
            .willReturn(aResponse().withTransformers(TestExpectationCallback.NAME)));
  }

  /**
   * Returns the request body as a string, transparently gunzipping it when the producer sent it
   * {@code Content-Encoding: gzip}. MockServer used to decompress request bodies automatically;
   * WireMock exposes the raw bytes.
   */
  private String bodyOf(LoggedRequest request) {
    byte[] body = request.getBody();
    String encoding = request.getHeader("Content-Encoding");
    if (encoding != null && encoding.toLowerCase().contains("gzip")) {
      try (java.util.zip.GZIPInputStream gis =
          new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(body))) {
        return new String(gis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      } catch (java.io.IOException e) {
        throw new RuntimeException(e);
      }
    }
    return new String(body, java.nio.charset.StandardCharsets.UTF_8);
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

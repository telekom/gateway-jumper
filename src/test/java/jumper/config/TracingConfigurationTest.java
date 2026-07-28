// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micrometer.observation.ObservationFilter;
import java.util.List;
import java.util.regex.Pattern;
import jumper.util.ExchangeStateManager;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.headers.observation.GatewayContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

public class TracingConfigurationTest {

  private static final String OBSERVATION_GATEWAY = "spring.cloud.gateway.http.client.requests";

  @Test
  void filterQueryParams() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered =
        new TracingConfiguration()
            .filterQueryParams(alreadyEncodedUri, List.of(Pattern.compile("nothing")));

    assertEquals(alreadyEncodedUri, filtered);
  }

  @Test
  void filterQueryParamsUnencodedEvenIfUrlIsInvalid() {
    String rawUri =
        "http://localhost:8080/actuator/health?sig=57DjUa/9u6KdgCgTZVrHzsm9ZOQA0U+3K+vqQ7PRrgc=";
    String filtered =
        new TracingConfiguration().filterQueryParams(rawUri, List.of(Pattern.compile("nothing")));

    assertEquals(rawUri, filtered);
  }

  @Test
  void filterBlacklistedQueryParameters() {
    String alreadyEncodedUri =
        "http://localhost:8080/actuator/health?sig-b=57DjUa%2F9u6KdgCgTZVrHzsm9ZOQA0U%2B3K%2BvqQ7PRrgc%3D";
    String filtered =
        new TracingConfiguration()
            .filterQueryParams(alreadyEncodedUri, List.of(Pattern.compile("sig-.*")));

    assertEquals("http://localhost:8080/actuator/health", filtered);
  }

  @Test
  void testFilterQueryParams() {
    // Test with unencoded URL containing spaces and special characters
    String unencodedUrl =
        "http://localhost:8080/foobar?$expand=ResourceToCharacteristic,ResourceToRelatedParty&$filter=ResourceType"
            + " eq 'FOO' and ResourceFilter eq 'ABC=8558 and DE=12' ";
    String filteredUnencoded =
        new TracingConfiguration()
            .filterQueryParams(unencodedUrl, List.of(Pattern.compile("sig-.*")));

    // Should return the URL with filtered query params (spaces preserved as-is, no sig-* params to
    // filter)
    assertEquals(
        "http://localhost:8080/foobar?$expand=ResourceToCharacteristic,ResourceToRelatedParty&$filter=ResourceType"
            + " eq 'FOO' and ResourceFilter eq 'ABC=8558 and DE=12' ",
        filteredUnencoded);

    // Test with encoded URL containing invalid characters in query param values
    // The '=' character in the encoded value causes IllegalArgumentException
    String encodedUrlWithInvalidChars =
        "http://localhost:8080/foobar?%24expand=ResourceToCharacteristic%2CResourceToRelatedParty&%24filter=ResourceType+eq+%27FOO%27+and+ResourceFilter+eq+%27ABC=8558+and+DE=12%27";
    String filteredEncoded =
        new TracingConfiguration()
            .filterQueryParams(encodedUrlWithInvalidChars, List.of(Pattern.compile("sig-.*")));

    // Should strip all query params when parsing fails due to invalid characters
    assertEquals("http://localhost:8080/foobar", filteredEncoded);
  }

  @Test
  void gatewayObservationUsesProviderSpanNameForNonMeshRoute() {
    // arrange
    MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/test").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    ExchangeStateManager.setMeshRoute(exchange, false);
    GatewayContext context = new GatewayContext(new HttpHeaders(), request, exchange);
    context.setName(OBSERVATION_GATEWAY);

    TracingConfiguration tracingConfiguration = new TracingConfiguration();
    tracingConfiguration.queryFilterList = List.of();
    ObservationFilter filter = tracingConfiguration.customSpanNameFilter();

    // act
    filter.map(context);

    // assert
    assertEquals("outgoing request: provider", context.getContextualName());
  }

  @Test
  void gatewayObservationUsesGatewaySpanNameForMeshRoute() {
    // arrange
    MockServerHttpRequest request = MockServerHttpRequest.get("/proxy/test").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    ExchangeStateManager.setMeshRoute(exchange, true);
    GatewayContext context = new GatewayContext(new HttpHeaders(), request, exchange);
    context.setName(OBSERVATION_GATEWAY);

    TracingConfiguration tracingConfiguration = new TracingConfiguration();
    tracingConfiguration.queryFilterList = List.of();
    ObservationFilter filter = tracingConfiguration.customSpanNameFilter();

    // act
    filter.map(context);

    // assert
    assertEquals("outgoing request: gateway", context.getContextualName());
  }
}

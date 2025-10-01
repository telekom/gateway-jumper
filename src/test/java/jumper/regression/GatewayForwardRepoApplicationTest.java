// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.regression;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/*
 * regression for https://github.com/spring-cloud/spring-cloud-gateway/issues/3677
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.main.cloud-platform=kubernetes")
@Import(CloudXForwardedConfiguration.class)
public class GatewayForwardRepoApplicationTest {

  private static ClientAndServer mockserver;

  @LocalServerPort private int port;

  private String uri;

  @BeforeEach
  void setUp() {
    uri = "http://localhost:" + port + "/get?value=a+nice+response";
  }

  @BeforeAll
  static void startMockServer() {
    mockserver = ClientAndServer.startClientAndServer(0);
    mockserver
        .when(request().withMethod("GET"))
        .respond(HttpResponse.response().withStatusCode(200).withBody("a nice response"));
  }

  @AfterAll
  static void stopMockServer() {
    mockserver.stop();
  }

  @DynamicPropertySource
  static void dynamicProperty(DynamicPropertyRegistry registry) {
    registry.add("mockserver.port", () -> mockserver.getPort());
  }

  @Test
  void niceRequest() {
    var result = RestClient.create().get().uri(uri).retrieve().toEntity(String.class);

    assertEquals(HttpStatus.OK, result.getStatusCode(), "Body\n%s".formatted(result.getBody()));
    assertNotNull(result.getBody());
    var body = result.getBody();
    assertNotNull(body);
    assertTrue(body.contains("a nice response"), "Contained instead\n%s".formatted(body));
  }

  @Test
  void evilRequest() {
    var result =
        RestClient.create()
            .get()
            .uri(uri)
            .header("X-Forwarded-For", "192.123.23.2")
            .header("X-Forwarded-Host", "example.org")
            .header("X-Forwarded-Port", "443")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Prefix", "/evil")
            .retrieve()
            .toEntity(String.class);

    assertEquals(HttpStatus.OK, result.getStatusCode(), "Body\n%s".formatted(result.getBody()));
    assertNotNull(result.getBody());
    var body = result.getBody();
    assertNotNull(body);
    assertTrue(body.contains("a nice response"), "Body\n%s".formatted(body));
  }
}

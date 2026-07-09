// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.ObjectMapperUtil;
import jumper.util.TokenUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import tools.jackson.databind.json.JsonMapper;

class JumperConfigResolverTest {

  private static final String FIRST_ZONE = "zone-a";
  private static final String SECOND_ZONE = "zone-b";
  private static final String FIRST_URL = "http://first.example.com/api";
  private static final String SECOND_URL = "http://second.example.com/api";
  private static final String SECONDARY_URL = "http://secondary.example.com/api";
  private static final String LEGACY_URL = "http://legacy.example.com/api";

  private final ZoneHealthCheckService zoneHealthCheckService = mock(ZoneHealthCheckService.class);
  private final JumperConfigHeaderReader headerReader = new JumperConfigHeaderReader();
  private final JumperConfigRequestEnricher requestEnricher =
      new JumperConfigRequestEnricher(headerReader);
  private final JumperConfigResolver resolver =
      new JumperConfigResolver(zoneHealthCheckService, headerReader, requestEnricher);

  @BeforeAll
  static void initObjectMapper() {
    // JumperConfig header serialization normally uses the Spring-managed mapper.
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

  @Test
  void resolve_fillsSingleRouteLegacyHeadersAndConsumerContext() {
    // arrange
    ServerHttpRequest request =
        MockServerHttpRequest.get("/")
            .header(Constants.HEADER_AUTHORIZATION, "Bearer " + TokenUtil.getConsumerAccessToken())
            .header(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toJsonBase64(new JumperConfig()))
            .header(Constants.HEADER_REMOTE_API_URL, LEGACY_URL)
            .header(Constants.HEADER_REALM, "sit")
            .header(Constants.HEADER_ENVIRONMENT, "test")
            .build();

    // act
    JumperConfig config = resolver.resolve(request);

    // assert
    assertEquals(LEGACY_URL, config.getRemoteApiUrl());
    assertEquals("sit", config.getRealmName());
    assertEquals("test", config.getEnvName());
    assertEquals("eni--local-team--local-app", config.getConsumer());
    assertEquals("localZone", config.getConsumerOriginZone());
    assertEquals("https://zone.local.de", config.getConsumerOriginStargate());
  }

  @Test
  void resolve_failsSingleRouteRoutingBeforeTokenParsing() {
    // arrange
    ServerHttpRequest request =
        MockServerHttpRequest.get("/")
            .header(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toJsonBase64(new JumperConfig()))
            .build();

    // act
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> resolver.resolve(request));

    // assert
    assertEquals(
        "missing routing information remote_api_url / jc.loadBalancing", exception.getMessage());
  }

  @Test
  void resolve_selectsFirstHealthyRoutingConfig() {
    // arrange
    when(zoneHealthCheckService.getZoneHealth(FIRST_ZONE)).thenReturn(true);
    ServerHttpRequest request =
        requestWithRoutingConfigs(
            proxyConfig(FIRST_ZONE, FIRST_URL), proxyConfig(SECOND_ZONE, SECOND_URL));

    // act
    JumperConfig config = resolver.resolve(request);

    // assert
    assertEquals(FIRST_URL, config.getRemoteApiUrl());
    assertEquals(FIRST_ZONE, config.getTargetZoneName());
    assertFalse(config.getSecondaryFailover());
  }

  @Test
  void resolve_skipsForcedZoneAndSelectsNextRoutingConfig() {
    // arrange
    when(zoneHealthCheckService.getZoneHealth(SECOND_ZONE)).thenReturn(true);
    ServerHttpRequest request =
        mutateWithSkipZone(
            requestWithRoutingConfigs(
                proxyConfig(FIRST_ZONE, FIRST_URL), proxyConfig(SECOND_ZONE, SECOND_URL)));

    // act
    JumperConfig config = resolver.resolve(request);

    // assert
    assertEquals(SECOND_URL, config.getRemoteApiUrl());
    assertEquals(SECOND_ZONE, config.getTargetZoneName());
  }

  @Test
  void resolve_marksBlankTargetZoneAsSecondaryFailover() {
    // arrange
    ServerHttpRequest request =
        requestWithRoutingConfigs(proxyConfig(FIRST_ZONE, FIRST_URL), secondaryConfig());
    when(zoneHealthCheckService.getZoneHealth(FIRST_ZONE)).thenReturn(false);

    // act
    JumperConfig config = resolver.resolve(request);

    // assert
    assertEquals(SECONDARY_URL, config.getRemoteApiUrl());
    assertTrue(config.getSecondaryFailover());
  }

  private static ServerHttpRequest requestWithRoutingConfigs(JumperConfig... configs) {
    return MockServerHttpRequest.get("/")
        .header(Constants.HEADER_AUTHORIZATION, "Bearer " + TokenUtil.getConsumerAccessToken())
        .header(Constants.HEADER_ROUTING_CONFIG, JumperConfig.toJsonBase64(List.of(configs)))
        .header(Constants.HEADER_JUMPER_CONFIG, JumperConfig.toJsonBase64(new JumperConfig()))
        .build();
  }

  private static ServerHttpRequest mutateWithSkipZone(ServerHttpRequest request) {
    return request.mutate().header(Constants.HEADER_X_FAILOVER_SKIP_ZONE, FIRST_ZONE).build();
  }

  private static JumperConfig proxyConfig(String targetZone, String remoteApiUrl) {
    JumperConfig config = new JumperConfig();
    config.setTargetZoneName(targetZone);
    config.setRemoteApiUrl(remoteApiUrl);
    config.setRealmName(Constants.DEFAULT_REALM);
    return config;
  }

  private static JumperConfig secondaryConfig() {
    JumperConfig config = new JumperConfig();
    config.setRemoteApiUrl(SECONDARY_URL);
    config.setRealmName(Constants.DEFAULT_REALM);
    return config;
  }
}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.request.HeaderConfig;
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
  private final RequestHeaderParser headerParser = new RequestHeaderParser();
  private final JumperConfigResolver resolver =
      new JumperConfigResolver(zoneHealthCheckService, headerParser);

  @BeforeAll
  static void initObjectMapper() {
    // JumperConfig header serialization normally uses the Spring-managed mapper.
    new ObjectMapperUtil(JsonMapper.builder().build());
  }

  @Test
  void resolve_keepsSingleRouteConfigAndHeadersSeparate() {
    // arrange
    ServerHttpRequest request =
        MockServerHttpRequest.get("/")
            .header(Constants.HEADER_AUTHORIZATION, "Bearer " + TokenUtil.getConsumerAccessToken())
            .header(
                Constants.HEADER_JUMPER_CONFIG,
                RequestHeaderParser.toJsonBase64(new JumperConfig()))
            .header(Constants.HEADER_REMOTE_API_URL, LEGACY_URL)
            .header(Constants.HEADER_REALM, "sit")
            .header(Constants.HEADER_ENVIRONMENT, "test")
            .build();

    // act
    HeaderConfig headers = headerParser.readHeaderConfig(request);
    JumperConfig config = resolver.resolve(request, headers);

    // assert
    assertNull(config.getRemoteApiUrl());
    assertNull(config.getRealmName());
    assertEquals(LEGACY_URL, headers.remoteApiUrl());
    assertEquals("sit", headers.realm());
    assertEquals("test", headers.environment());
    assertFalse(headers.hasRoutingConfigHeader());
  }

  @Test
  void resolve_selectsFirstHealthyRoutingConfig() {
    // arrange
    when(zoneHealthCheckService.getZoneHealth(FIRST_ZONE)).thenReturn(true);
    ServerHttpRequest request =
        requestWithRoutingConfigs(
            proxyConfig(FIRST_ZONE, FIRST_URL), proxyConfig(SECOND_ZONE, SECOND_URL));

    // act
    HeaderConfig headers = headerParser.readHeaderConfig(request);
    JumperConfig config = resolver.resolve(request, headers);

    // assert
    assertEquals(FIRST_URL, config.getRemoteApiUrl());
    assertEquals(FIRST_ZONE, config.getTargetZoneName());
    assertTrue(headers.hasRoutingConfigHeader());
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
    HeaderConfig headers = headerParser.readHeaderConfig(request);
    JumperConfig config = resolver.resolve(request, headers);

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
    HeaderConfig headers = headerParser.readHeaderConfig(request);
    JumperConfig config = resolver.resolve(request, headers);

    // assert
    assertEquals(SECONDARY_URL, config.getRemoteApiUrl());
    assertTrue(headers.hasRoutingConfigHeader());
    assertTrue(config.getTargetZoneName() == null || config.getTargetZoneName().isEmpty());
  }

  private static ServerHttpRequest requestWithRoutingConfigs(JumperConfig... configs) {
    return MockServerHttpRequest.get("/")
        .header(Constants.HEADER_AUTHORIZATION, "Bearer " + TokenUtil.getConsumerAccessToken())
        .header(Constants.HEADER_ROUTING_CONFIG, RequestHeaderParser.toJsonBase64(List.of(configs)))
        .header(
            Constants.HEADER_JUMPER_CONFIG, RequestHeaderParser.toJsonBase64(new JumperConfig()))
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

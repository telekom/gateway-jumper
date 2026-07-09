// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class JumperConfigResolver {

  private final ZoneHealthCheckService zoneHealthCheckService;
  private final JumperConfigHeaderReader headerReader;
  private final JumperConfigRequestEnricher requestEnricher;

  public JumperConfig resolve(ServerHttpRequest request) {
    if (request.getHeaders().containsHeader(Constants.HEADER_ROUTING_CONFIG)) {
      return resolveSelectedRoutingConfig(request);
    }

    return resolveSingleRouteConfig(request);
  }

  private JumperConfig resolveSingleRouteConfig(ServerHttpRequest request) {
    JumperConfig config = headerReader.readJumperConfig(request);
    requestEnricher.applySingleRouteHeaderFallbacks(config, request);
    requestEnricher.resolveSingleRouteRemoteApiUrl(config);
    requestEnricher.applyConsumerTokenContext(config, request);
    log.debug("JumperConfig decoded: {}", config);
    return config;
  }

  private JumperConfig resolveSelectedRoutingConfig(ServerHttpRequest request) {
    List<JumperConfig> routingConfigs = headerReader.readRoutingConfigs(request);
    log.debug("failover case, routing_config: {}", routingConfigs);

    JumperConfig selectedConfig =
        selectHealthyRoutingConfig(
            routingConfigs, request.getHeaders().getFirst(Constants.HEADER_X_FAILOVER_SKIP_ZONE));

    requestEnricher.applySelectedRoutingConfigFallbacks(selectedConfig, request);
    requestEnricher.applyConsumerTokenContext(selectedConfig, request);
    requestEnricher.applySpectreContextFromJumperConfigHeader(selectedConfig, request);
    requestEnricher.resolveSelectedRoutingConfigRemoteApiUrl(selectedConfig);

    log.debug("failover case, enhanced jumper_config: {}", selectedConfig);
    return selectedConfig;
  }

  private JumperConfig selectHealthyRoutingConfig(
      List<JumperConfig> routingConfigs, String forceSkipZone) {
    for (JumperConfig config : routingConfigs) {
      if (StringUtils.isEmpty(config.getTargetZoneName())) {
        config.setSecondaryFailover(true);
        return config;
      }

      if (!(config.getTargetZoneName().equalsIgnoreCase(forceSkipZone)
          || !zoneHealthCheckService.getZoneHealth(config.getTargetZoneName()))) {
        return config;
      }
    }

    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Non of defined failover zones available");
  }
}

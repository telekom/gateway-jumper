// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.request.HeaderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Selects the wire configuration applicable to the current request.
 *
 * <p>Normal requests use the top-level {@code jumper_config}. Requests carrying {@code
 * routing_config} select the first eligible healthy routing entry. This class does not merge the
 * selected configuration with legacy headers; effective value precedence is owned by {@link
 * EffectiveRequestConfigResolver}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JumperConfigResolver {

  private final ZoneHealthCheckService zoneHealthCheckService;
  private final RequestHeaderParser headerParser;

  public JumperConfig resolve(ServerHttpRequest request, HeaderConfig headers) {
    if (headers.hasRoutingConfigHeader()) {
      return resolveSelectedRoutingConfig(request);
    }

    JumperConfig topLevelConfig = headerParser.readJumperConfig(request);
    log.debug("JumperConfig decoded: {}", topLevelConfig);
    return topLevelConfig;
  }

  private JumperConfig resolveSelectedRoutingConfig(ServerHttpRequest request) {
    List<JumperConfig> routingConfigs = headerParser.readRoutingConfigs(request);
    log.debug("failover case, routing_config: {}", routingConfigs);

    JumperConfig selectedConfig =
        selectHealthyRoutingConfig(
            routingConfigs, request.getHeaders().getFirst(Constants.HEADER_X_FAILOVER_SKIP_ZONE));
    log.debug("failover case, selected routing_config: {}", selectedConfig);
    return selectedConfig;
  }

  private JumperConfig selectHealthyRoutingConfig(
      List<JumperConfig> routingConfigs, String forceSkipZone) {
    for (JumperConfig config : routingConfigs) {
      // A blank target zone marks the unconditional secondary/provider fallback entry.
      if (StringUtils.isEmpty(config.getTargetZoneName())) {
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

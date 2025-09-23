// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
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
public class JumperConfigService {

  private final ZoneHealthCheckService zoneHealthCheckService;

  public JumperConfig resolveJumperConfig(ServerHttpRequest readOnlyRequest) {
    JumperConfig jumperConfig;
    // failover logic if routing_config header present
    if (readOnlyRequest.getHeaders().containsKey(Constants.HEADER_ROUTING_CONFIG)) {
      // evaluate routingConfig for failover scenario
      List<JumperConfig> jumperConfigList = JumperConfig.parseJumperConfigListFrom(readOnlyRequest);
      log.debug("failover case, routing_config: {}", jumperConfigList);
      jumperConfig =
          pickConfigForHealthyTargetZone(
              jumperConfigList,
              readOnlyRequest.getHeaders().getFirst(Constants.HEADER_X_FAILOVER_SKIP_ZONE));
      jumperConfig.fillProcessingInfo(readOnlyRequest);
      log.debug("failover case, enhanced jumper_config: {}", jumperConfig);

    }

    // no failover
    else {
      // Prepare and extract JumperConfigValues
      jumperConfig = JumperConfig.parseAndFillJumperConfigFrom(readOnlyRequest);
      log.debug("JumperConfig decoded: {}", jumperConfig);
    }
    return jumperConfig;
  }

  private JumperConfig pickConfigForHealthyTargetZone(
      List<JumperConfig> jumperConfigList, String forceSkipZone) {
    for (JumperConfig jc : jumperConfigList) {
      // secondary route, failover in place => audit logs
      if (StringUtils.isEmpty(jc.getTargetZoneName())) {
        jc.setSecondaryFailover(true);
        return jc;
      }
      // targetZoneName present, check it against force skip header and zones state
      // map
      if (!(jc.getTargetZoneName().equalsIgnoreCase(forceSkipZone)
          || !zoneHealthCheckService.getZoneHealth(jc.getTargetZoneName()))) {
        return jc;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE, "Non of defined failover zones available");
  }
}

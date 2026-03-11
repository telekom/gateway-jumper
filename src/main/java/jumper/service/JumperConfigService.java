// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.List;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.HeaderUtil;
import jumper.util.JsonConverter;
import jumper.util.LoadBalancingUtil;
import jumper.util.OauthTokenUtil;
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
  private final JsonConverter jsonConverter;

  public JumperConfig resolveJumperConfig(ServerHttpRequest readOnlyRequest) {
    JumperConfig jumperConfig;
    // failover logic if routing_config header present
    if (readOnlyRequest.getHeaders().containsKey(Constants.HEADER_ROUTING_CONFIG)) {
      // evaluate routingConfig for failover scenario
      List<JumperConfig> jumperConfigList = parseJumperConfigListFrom(readOnlyRequest);
      log.debug("failover case, routing_config: {}", jumperConfigList);
      jumperConfig =
          pickConfigForHealthyTargetZone(
              jumperConfigList,
              readOnlyRequest.getHeaders().getFirst(Constants.HEADER_X_FAILOVER_SKIP_ZONE));
      fillProcessingInfo(readOnlyRequest, jumperConfig);
      log.debug("failover case, enhanced jumper_config: {}", jumperConfig);

    }

    // no failover
    else {
      // Prepare and extract JumperConfigValues
      jumperConfig = parseAndFillJumperConfigFrom(readOnlyRequest);
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

  private List<JumperConfig> parseJumperConfigListFrom(ServerHttpRequest request) {

    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isNotBlank(routingConfigBase64)) {
      return jsonConverter.fromJsonBase64(routingConfigBase64, new TypeReference<>() {});
    }

    throw new RuntimeException("can not base64decode header: " + routingConfigBase64);
  }

  private JumperConfig parseAndFillJumperConfigFrom(ServerHttpRequest request) {

    JumperConfig jc =
        fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));

    fillWithLegacyHeaders(
        request, jc); // TODO: remove as soon we have completely shifted to json_config

    return jc;
  }

  public JumperConfig fromJsonBase64(String jsonConfigBase64) {
    if (StringUtils.isNotBlank(jsonConfigBase64)) {
      return jsonConverter.fromJsonBase64(jsonConfigBase64, new TypeReference<>() {});
    } else {
      return new JumperConfig();
    }
  }

  private void fillProcessingInfo(ServerHttpRequest request, JumperConfig jumperConfig) {
    jumperConfig.setConsumerToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(jumperConfig.getConsumerToken()));
    jumperConfig.setConsumer(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    jumperConfig.setConsumerOriginStargate(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    jumperConfig.setConsumerOriginZone(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));

    // Spectre stuff
    JumperConfig jc =
        fromJsonBase64(
            HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
    jumperConfig.setRouteListener(jc.getRouteListener());
    jumperConfig.setGatewayClient(jc.getGatewayClient());

    // check loadBalancing
    if (Objects.nonNull(jumperConfig.getLoadBalancing())
        && !jumperConfig.getLoadBalancing().getServers().isEmpty()) {
      jumperConfig.setRemoteApiUrl(
          LoadBalancingUtil.calculateUpstream(jumperConfig.getLoadBalancing().getServers()));
    } else if (Objects.isNull(jumperConfig.getRemoteApiUrl())) {
      throw new RuntimeException("missing routing information jc.remoteApiUrl / jc.loadBalancing");
    }
  }

  private void fillWithLegacyHeaders(ServerHttpRequest request, JumperConfig jc) {

    // proxy & real
    if (request.getHeaders().containsKey(Constants.HEADER_REMOTE_API_URL)) {
      jc.setRemoteApiUrl(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REMOTE_API_URL));
    } else if (Objects.nonNull(jc.getLoadBalancing())
        && !jc.getLoadBalancing().getServers().isEmpty()) {
      jc.setRemoteApiUrl(LoadBalancingUtil.calculateUpstream(jc.getLoadBalancing().getServers()));
    } else {
      throw new RuntimeException(
          "missing routing information " + Constants.HEADER_REMOTE_API_URL + " / jc.loadBalancing");
    }

    // proxy
    jc.setInternalTokenEndpoint(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ISSUER));
    jc.setClientId(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_CLIENT_ID)); // also external
    jc.setClientSecret(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_CLIENT_SECRET)); // also external

    // real
    jc.setApiBasePath(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_API_BASE_PATH));
    if (request.getHeaders().containsKey(Constants.HEADER_ACCESS_TOKEN_FORWARDING)) {
      jc.setAccessTokenForwarding(
          Boolean.valueOf(
              HeaderUtil.getLastValueFromHeaderField(
                  request, Constants.HEADER_ACCESS_TOKEN_FORWARDING)));
    }
    jc.setRealmName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REALM));
    if (StringUtils.isBlank(jc.getRealmName())) {
      jc.setRealmName(Constants.DEFAULT_REALM);
    }
    jc.setEnvName(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ENVIRONMENT));

    // external oauth
    jc.setScopes(HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SCOPES));
    jc.setExternalTokenEndpoint(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_TOKEN_ENDPOINT));
    jc.setXSpacegateClientId(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID));
    jc.setXSpacegateClientSecret(
        HeaderUtil.getLastValueFromHeaderField(
            request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET));
    jc.setXSpacegateScope(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_SCOPE));

    // processing
    jc.setConsumerToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));
    Jwt<?, Claims> consumerTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(jc.getConsumerToken()));
    jc.setConsumer(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    jc.setConsumerOriginStargate(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    jc.setConsumerOriginZone(
        consumerTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));
  }
}

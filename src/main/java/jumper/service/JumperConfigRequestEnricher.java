// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.util.HeaderUtil;
import jumper.util.LoadBalancingUtil;
import jumper.util.OauthTokenUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JumperConfigRequestEnricher {

  private final JumperConfigHeaderReader headerReader;

  public void applySingleRouteHeaderFallbacks(JumperConfig config, ServerHttpRequest request) {
    applyRemoteApiUrlHeaderFallback(config, request);
    applyLegacyMeshHeaderFallbacks(config, request);
    applyProviderRouteHeaderFallbacks(config, request);
    applyExternalOauthHeaderFallbacks(config, request);
  }

  public void applySelectedRoutingConfigFallbacks(JumperConfig config, ServerHttpRequest request) {
    applyRealmFallback(config, request);
    applyEnvironmentFallback(config, request);
  }

  public void applyConsumerTokenContext(JumperConfig config, ServerHttpRequest request) {
    config.setAuthorizationToken(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION));

    Jwt<?, Claims> authorizationTokenClaims =
        OauthTokenUtil.getAllClaimsFromToken(config.getAuthorizationToken());

    config.setConsumer(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_CLIENT_ID, String.class));
    config.setConsumerOriginStargate(
        authorizationTokenClaims
            .getBody()
            .get(Constants.TOKEN_CLAIM_ORIGIN_STARGATE, String.class));
    config.setConsumerOriginZone(
        authorizationTokenClaims.getBody().get(Constants.TOKEN_CLAIM_ORIGIN_ZONE, String.class));
  }

  public void applySpectreContextFromJumperConfigHeader(
      JumperConfig selectedConfig, ServerHttpRequest request) {
    JumperConfig topLevelConfig = headerReader.readJumperConfig(request);
    selectedConfig.setRouteListener(topLevelConfig.getRouteListener());
    selectedConfig.setGatewayClient(topLevelConfig.getGatewayClient());
  }

  public void resolveSingleRouteRemoteApiUrl(JumperConfig config) {
    resolveRemoteApiUrl(
        config,
        "missing routing information " + Constants.HEADER_REMOTE_API_URL + " / jc.loadBalancing");
  }

  public void resolveSelectedRoutingConfigRemoteApiUrl(JumperConfig config) {
    resolveRemoteApiUrl(config, "missing routing information jc.remoteApiUrl / jc.loadBalancing");
  }

  private void resolveRemoteApiUrl(JumperConfig config, String missingRoutingInformationMessage) {
    if (Objects.nonNull(config.getLoadBalancing())
        && !config.getLoadBalancing().getServers().isEmpty()) {
      config.setRemoteApiUrl(
          LoadBalancingUtil.calculateUpstream(config.getLoadBalancing().getServers()));
      return;
    }

    if (StringUtils.isBlank(config.getRemoteApiUrl())) {
      throw new RuntimeException(missingRoutingInformationMessage);
    }
  }

  private void applyRemoteApiUrlHeaderFallback(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isBlank(config.getRemoteApiUrl())
        && request.getHeaders().containsHeader(Constants.HEADER_REMOTE_API_URL)) {
      config.setRemoteApiUrl(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REMOTE_API_URL));
    }
  }

  private void applyLegacyMeshHeaderFallbacks(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isBlank(config.getInternalTokenEndpoint())) {
      config.setInternalTokenEndpoint(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ISSUER));
    }
    if (StringUtils.isBlank(config.getClientId())) {
      config.setClientId(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_ID));
    }
    if (StringUtils.isBlank(config.getClientSecret())) {
      config.setClientSecret(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SECRET));
    }
  }

  private void applyProviderRouteHeaderFallbacks(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isBlank(config.getApiBasePath())) {
      config.setApiBasePath(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_API_BASE_PATH));
    }
    if (Objects.isNull(config.getAccessTokenForwarding())
        && request.getHeaders().containsHeader(Constants.HEADER_ACCESS_TOKEN_FORWARDING)) {
      config.setAccessTokenForwarding(
          Boolean.valueOf(
              HeaderUtil.getLastValueFromHeaderField(
                  request, Constants.HEADER_ACCESS_TOKEN_FORWARDING)));
    }

    applyRealmFallback(config, request);
    applyEnvironmentFallback(config, request);
  }

  private void applyExternalOauthHeaderFallbacks(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isBlank(config.getScopes())) {
      config.setScopes(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_CLIENT_SCOPES));
    }
    if (StringUtils.isBlank(config.getExternalTokenEndpoint())) {
      config.setExternalTokenEndpoint(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_TOKEN_ENDPOINT));
    }
    if (StringUtils.isBlank(config.getXSpacegateClientId())) {
      config.setXSpacegateClientId(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID));
    }
    if (StringUtils.isBlank(config.getXSpacegateClientSecret())) {
      config.setXSpacegateClientSecret(
          HeaderUtil.getLastValueFromHeaderField(
              request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET));
    }
    if (StringUtils.isBlank(config.getXSpacegateScope())) {
      config.setXSpacegateScope(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_X_SPACEGATE_SCOPE));
    }
  }

  private void applyRealmFallback(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isNotBlank(config.getRealmName())) {
      return;
    }

    // TODO: remove legacy realm header and issuer fallbacks after Mesh LMS phase 2 completes.
    String legacyRealmHeader =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_REALM);
    if (StringUtils.isNotBlank(legacyRealmHeader)) {
      config.setRealmName(legacyRealmHeader);
      return;
    }

    if (StringUtils.isNotBlank(config.getInternalTokenEndpoint())) {
      config.setRealmName(config.getInternalTokenEndpoint().replaceFirst(".*realms/", ""));
      return;
    }

    config.setRealmName(Constants.DEFAULT_REALM);
  }

  private void applyEnvironmentFallback(JumperConfig config, ServerHttpRequest request) {
    if (StringUtils.isBlank(config.getEnvName())) {
      config.setEnvName(
          HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ENVIRONMENT));
    }
  }
}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Objects;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.OauthCredentials;
import jumper.model.request.HeaderConfig;
import jumper.util.LoadBalancingUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves effective request configuration values from the selected wire configuration and legacy
 * request headers.
 *
 * <p>{@link JumperConfigResolver} first selects the applicable top-level or routing configuration.
 * This resolver then applies the compatibility precedence between that {@link JumperConfig} and
 * {@link HeaderConfig}. Values from a selected routing configuration do not inherit target or
 * authentication fields from legacy headers unless a method explicitly permits that fallback.
 *
 * <p>TODO: remove legacy header fallbacks once configuration has completely shifted to JSON
 * configuration.
 */
@Component
public class EffectiveRequestConfigResolver {

  public String resolveUpstreamUrl(JumperConfig config, HeaderConfig headers) {
    if (Objects.nonNull(config.getLoadBalancing())
        && Objects.nonNull(config.getLoadBalancing().getServers())
        && !config.getLoadBalancing().getServers().isEmpty()) {
      return LoadBalancingUtil.calculateUpstream(config.getLoadBalancing().getServers());
    }
    if (StringUtils.isNotBlank(config.getRemoteApiUrl())) {
      return config.getRemoteApiUrl();
    }
    if (!headers.hasRoutingConfigHeader() && StringUtils.isNotBlank(headers.remoteApiUrl())) {
      return headers.remoteApiUrl();
    }
    throw new RuntimeException(
        headers.hasRoutingConfigHeader()
            ? "missing routing information jc.remoteApiUrl / jc.loadBalancing"
            : "missing routing information remote_api_url / jc.loadBalancing");
  }

  public String resolveApiBasePath(JumperConfig config, HeaderConfig headers) {
    if (StringUtils.isNotBlank(config.getApiBasePath())) {
      return config.getApiBasePath();
    }
    return headers.hasRoutingConfigHeader() ? null : headers.apiBasePath();
  }

  public String resolveRealmName(JumperConfig config, HeaderConfig headers) {
    String issuer = resolveInternalTokenEndpoint(config, headers);
    String realm = config.getRealmName();
    // TODO: remove legacy realm header and issuer fallbacks after Mesh LMS phase 2 completes.
    if (StringUtils.isBlank(realm)) {
      realm = headers.realm();
    }
    if (StringUtils.isBlank(realm) && StringUtils.isNotBlank(issuer)) {
      realm = issuer.replaceFirst(".*realms/", "");
    }

    return StringUtils.defaultIfBlank(realm, Constants.DEFAULT_REALM);
  }

  public String resolveEnvironment(JumperConfig config, HeaderConfig headers) {
    return StringUtils.defaultIfBlank(config.getEnvName(), headers.environment());
  }

  public String resolveInternalTokenEndpoint(JumperConfig config, HeaderConfig headers) {
    if (StringUtils.isNotBlank(config.getInternalTokenEndpoint())) {
      return config.getInternalTokenEndpoint();
    }
    return headers.hasRoutingConfigHeader() ? null : headers.issuer();
  }

  public String resolveExternalTokenEndpoint(JumperConfig config, HeaderConfig headers) {
    if (StringUtils.isNotBlank(config.getExternalTokenEndpoint())) {
      return config.getExternalTokenEndpoint();
    }
    return headers.hasRoutingConfigHeader() ? null : headers.tokenEndpoint();
  }

  public String resolveLmsSecurityScopes(JumperConfig config, String clientId) {
    if (Objects.isNull(config.getOauth())) {
      return null;
    }
    OauthCredentials credentials = config.getOauth().get(clientId);
    if (Objects.isNull(credentials)) {
      credentials = config.getOauth().get(Constants.OAUTH_PROVIDER_KEY);
    }
    return Objects.nonNull(credentials) ? credentials.getScopes() : null;
  }
}

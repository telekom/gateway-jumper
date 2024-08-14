// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.*;
import static jumper.model.config.JumperConfig.toBase64;

import java.util.List;
import java.util.function.Consumer;
import jumper.BaseSteps;
import jumper.Constants;
import jumper.model.config.*;
import org.springframework.http.HttpHeaders;

public class RoutingConfigUtil {

  public static Consumer<HttpHeaders> getSecondaryRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcSecondary(baseSteps.getId()));
    };
  }

  public static Consumer<HttpHeaders> getSecondaryRouteHeadersWithLoadbalancing(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(
          Constants.HEADER_ROUTING_CONFIG, getRcSecondaryLoadbalancing(baseSteps.getId()));
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxy(baseSteps.getId()));
    };
  }

  public static String getRcSecondary(String id) {
    // proxy + real
    return toBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME, id), getRealRouteJc()));
  }

  public static String getRcSecondaryLoadbalancing(String id) {
    // proxy + real (with loadbalancing)
    return toBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME, id), getRealRouteJcLb()));
  }

  public static String getRcProxy(String id) {
    // proxy + proxy
    return toBase64(
        List.of(
            getProxyRouteJc(REMOTE_ZONE_NAME, id), getProxyRouteJc(REMOTE_FAILOVER_ZONE_NAME, id)));
  }

  private static JumperConfig getProxyRouteJc(String targetZone, String id) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/default");
    jc.setClientId(addIdSuffix("stargate", id));
    jc.setClientSecret("secret");

    switch (targetZone) {
      case REMOTE_ZONE_NAME -> {
        jc.setTargetZoneName(REMOTE_ZONE_NAME);
        jc.setRemoteApiUrl(REMOTE_HOST + REMOTE_BASE_PATH);
      }
      case REMOTE_FAILOVER_ZONE_NAME -> {
        jc.setTargetZoneName(REMOTE_FAILOVER_ZONE_NAME);
        jc.setRemoteApiUrl(REMOTE_HOST + REMOTE_FAILOVER_BASE_PATH);
      }
    }
    return jc;
  }

  private static JumperConfig getRealRouteJc() {
    JumperConfig jc = new JumperConfig();
    jc.setRemoteApiUrl(REMOTE_HOST + REMOTE_PROVIDER_BASE_PATH);
    jc.setApiBasePath(BASE_PATH);
    jc.setRealmName(REALM);
    jc.setEnvName(ENVIRONMENT);
    jc.setAccessTokenForwarding(false);
    return jc;
  }

  private static JumperConfig getRealRouteJcLb() {
    JumperConfig jc = new JumperConfig();
    LoadBalancing loadBalancing = new LoadBalancing();
    loadBalancing.setServers(
        List.of(
            new Server(REMOTE_HOST + REMOTE_PROVIDER_BASE_PATH, 50.0),
            new Server(REMOTE_HOST + REMOTE_PROVIDER_BASE_PATH, 50.0)));
    jc.setLoadBalancing(loadBalancing);
    jc.setApiBasePath(BASE_PATH);
    jc.setRealmName(REALM);
    jc.setEnvName(ENVIRONMENT);
    jc.setAccessTokenForwarding(false);
    return jc;
  }

  public static String addIdSuffix(String from, String id) {
    return from + "_" + id;
  }
}

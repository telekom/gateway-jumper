// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.*;
import static jumper.service.RequestHeaderParser.toJsonBase64;

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
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcSecondary());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getSecondaryRouteHeadersWithLoadbalancing(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcSecondaryLoadbalancing());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getSecondaryRouteHeadersWithConflictingRemoteApiUrl(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, REMOTE_HOST + REMOTE_CONFLICTING_BASE_PATH);
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcSecondary());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxy());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithConsumerRouteListener(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      // Legacy control plane puts proxy failover target and gateway credentials in routing_config.
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyLegacyIssuer(baseSteps.getId()));
      httpHeaders.set(
          Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcRouteListener(CONSUMER));
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithRoutingConfigRealm(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyWithRoutingConfigRealm());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithLegacyRealmHeader(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REALM, NON_DEFAULT_REALM);
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxy());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuer(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyLegacyIssuer(baseSteps.getId()));
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuerWithNonDefaultRealm(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(
          Constants.HEADER_ROUTING_CONFIG,
          getRcProxyLegacyIssuerWithNonDefaultRealm(baseSteps.getId()));
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static String getRcSecondary() {
    // proxy + real
    return toJsonBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME), getRealRouteJc()));
  }

  public static String getRcSecondaryLoadbalancing() {
    // proxy + real (with loadbalancing)
    return toJsonBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME), getRealRouteJcLb()));
  }

  public static String getRcProxy() {
    // proxy + proxy
    return toJsonBase64(
        List.of(getProxyRouteJc(REMOTE_ZONE_NAME), getProxyRouteJc(REMOTE_FAILOVER_ZONE_NAME)));
  }

  public static String getRcProxyWithRoutingConfigRealm() {
    // proxy + proxy, selected entry realm wins over legacy header and issuer fallbacks
    return toJsonBase64(
        List.of(
            getProxyRouteJcWithRoutingConfigRealm(REMOTE_ZONE_NAME),
            getProxyRouteJc(REMOTE_FAILOVER_ZONE_NAME)));
  }

  public static String getRcProxyLegacyIssuer(String id) {
    // proxy + proxy, using the legacy issuer trigger as transitional fallback
    return toJsonBase64(
        List.of(
            getProxyRouteJcLegacyIssuer(REMOTE_ZONE_NAME, id),
            getProxyRouteJcLegacyIssuer(REMOTE_FAILOVER_ZONE_NAME, id)));
  }

  public static String getRcProxyLegacyIssuerWithNonDefaultRealm(String id) {
    // proxy + proxy, using the legacy issuer trigger and realm fallback as transitional fallback
    return toJsonBase64(
        List.of(
            getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_ZONE_NAME, id),
            getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_FAILOVER_ZONE_NAME, id)));
  }

  private static JumperConfig getProxyRouteJc(String targetZone) {
    JumperConfig jc = new JumperConfig();
    jc.setMesh(true);

    setProxyRouteTarget(jc, targetZone);
    return jc;
  }

  private static JumperConfig getProxyRouteJcWithRoutingConfigRealm(String targetZone) {
    JumperConfig jc = getProxyRouteJc(targetZone);
    jc.setRealmName(NON_DEFAULT_REALM);
    return jc;
  }

  private static JumperConfig getProxyRouteJcLegacyIssuer(String targetZone, String id) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/default");
    jc.setClientId(addIdSuffix("stargate", id));
    jc.setClientSecret("secret");

    setProxyRouteTarget(jc, targetZone);
    return jc;
  }

  private static JumperConfig getProxyRouteJcLegacyIssuerWithNonDefaultRealm(
      String targetZone, String id) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/" + NON_DEFAULT_REALM);
    jc.setClientId(addIdSuffix("stargate", id));
    jc.setClientSecret("secret");

    setProxyRouteTarget(jc, targetZone);
    return jc;
  }

  private static void setProxyRouteTarget(JumperConfig jc, String targetZone) {
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

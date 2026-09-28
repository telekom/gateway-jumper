// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.*;
import static jumper.model.config.JumperConfig.toJsonBase64;

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

  public static Consumer<HttpHeaders> getSecondaryRouteHeadersWithAudience(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcSecondaryWithAudience());
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

  public static Consumer<HttpHeaders> getProxyRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxy());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithRealmHeader(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REALM, NON_DEFAULT_REALM);
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxy());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithNonDefaultRealm(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyWithNonDefaultRealm());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getListenerRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcListener());
    };
  }

  public static String getRcListener() {
    // A listener route with zone failover configured: the zoned proxy entry the control plane
    // sends carries an issuer but no realm, and it is the entry selected while the zone is
    // healthy. The provider entry is the failover fallback.
    return toJsonBase64(
        List.of(
            onCallback(getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_ZONE_NAME)),
            onCallback(getRealRouteJc())));
  }

  public static Consumer<HttpHeaders> getListenerRouteHeadersProxyFailover(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcListenerProxyFailover());
    };
  }

  public static String getRcListenerProxyFailover() {
    // A listener route whose failover upstreams are all in other zones: every entry is a proxy
    // entry with an issuer but no realm. The primary entry names the default realm and the
    // failover entry a non-default one, so the realm shows which entry was used.
    return toJsonBase64(
        List.of(
            onCallback(getProxyRouteJcLegacyIssuer(REMOTE_ZONE_NAME)),
            onCallback(getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_FAILOVER_ZONE_NAME))));
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuer(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyLegacyIssuer());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuerWithNonDefaultRealm(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_ROUTING_CONFIG, getRcProxyLegacyIssuerWithNonDefaultRealm());
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static String getRcSecondary() {
    // proxy + real
    return toJsonBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME), getRealRouteJc()));
  }

  public static String getRcSecondaryWithAudience() {
    // proxy + real, where the real route carries a provider-configured literal audience
    JumperConfig realRoute = getRealRouteJc();
    realRoute.setClaims(
        JumperConfigUtil.defaultClaims(JumperConfigUtil.audienceClaim(CONFIGURED_AUDIENCE, null)));
    return toJsonBase64(List.of(getProxyRouteJc(REMOTE_ZONE_NAME), realRoute));
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

  public static String getRcProxyWithNonDefaultRealm() {
    // proxy + proxy, each entry carrying the realm the control plane assigned it
    JumperConfig primary = getProxyRouteJc(REMOTE_ZONE_NAME);
    primary.setRealmName(NON_DEFAULT_REALM);
    JumperConfig failover = getProxyRouteJc(REMOTE_FAILOVER_ZONE_NAME);
    failover.setRealmName(NON_DEFAULT_REALM);
    return toJsonBase64(List.of(primary, failover));
  }

  public static String getRcProxyLegacyIssuer() {
    // proxy + proxy, using the legacy issuer trigger as transitional fallback
    return toJsonBase64(
        List.of(
            getProxyRouteJcLegacyIssuer(REMOTE_ZONE_NAME),
            getProxyRouteJcLegacyIssuer(REMOTE_FAILOVER_ZONE_NAME)));
  }

  public static String getRcProxyLegacyIssuerWithNonDefaultRealm() {
    // proxy + proxy, using the legacy issuer trigger and realm fallback as transitional fallback
    return toJsonBase64(
        List.of(
            getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_ZONE_NAME),
            getProxyRouteJcLegacyIssuerWithNonDefaultRealm(REMOTE_FAILOVER_ZONE_NAME)));
  }

  private static JumperConfig getProxyRouteJc(String targetZone) {
    JumperConfig jc = new JumperConfig();
    jc.setMesh(true);

    setProxyRouteTarget(jc, targetZone);
    return jc;
  }

  private static JumperConfig getProxyRouteJcLegacyIssuer(String targetZone) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/default");
    jc.setClientId("stargate");
    jc.setClientSecret("secret");

    setProxyRouteTarget(jc, targetZone);
    return jc;
  }

  private static JumperConfig getProxyRouteJcLegacyIssuerWithNonDefaultRealm(String targetZone) {
    JumperConfig jc = new JumperConfig();
    jc.setInternalTokenEndpoint("http://localhost:1081/auth/realms/" + NON_DEFAULT_REALM);
    jc.setClientId("stargate");
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

  private static JumperConfig onCallback(JumperConfig jc) {
    // aim the route at the /callback stub the listener steps set up
    jc.setRemoteApiUrl(REMOTE_HOST);
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
}

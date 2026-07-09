// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.*;
import static jumper.util.JumperConfigUtil.addIdSuffix;

import java.util.function.Consumer;
import jumper.BaseSteps;
import jumper.Constants;
import org.springframework.http.HttpHeaders;

public class TokenUtil {

  public static Consumer<HttpHeaders> getJumperLmsHeaders(String consumerToken) {
    return httpHeaders -> {
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.setBearerAuth(consumerToken);
    };
  }

  public static Consumer<HttpHeaders> getJumperLmsHeaders() {
    return getJumperLmsHeaders(getConsumerAccessToken());
  }

  public static String getConsumerAccessToken() {
    AccessToken consumerAccessToken =
        AccessToken.builder()
            .env("local")
            .clientId("eni--local-team--local-app")
            .originZone("localZone")
            .originStargate("https://zone.local.de")
            .build();
    return consumerAccessToken.getConsumerAccessToken();
  }

  public static String getConsumerAccessTokenWithAud() {
    AccessToken consumerAccessToken =
        AccessToken.builder()
            .env("local")
            .clientId("eni--local-team--local-app")
            .originZone("localZone")
            .originStargate("https://zone.local.de")
            .audience("testAudience")
            .build();
    return consumerAccessToken.getConsumerAccessToken();
  }

  public static String getConsumerAccessTokenWithMultipleAud() {
    AccessToken consumerAccessToken =
        AccessToken.builder()
            .env("local")
            .clientId("eni--local-team--local-app")
            .originZone("localZone")
            .originStargate("https://zone.local.de")
            .audiences(java.util.List.of("testAudience1", "testAudience2"))
            .build();
    return consumerAccessToken.getConsumerAccessToken();
  }

  public static Consumer<HttpHeaders> getProxyRouteHeaders(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithJumperConfigRealm(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(
          Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMeshWithRealm(NON_DEFAULT_REALM));
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithLegacyRealmHeader(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(Constants.HEADER_REALM, NON_DEFAULT_REALM);
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersWithXtokenExchange(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, JumperConfigUtil.getJcMesh());
      httpHeaders.set(Constants.HEADER_X_TOKEN_EXCHANGE, "Bearer XTokenExchangeHeader");
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuer(BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(Constants.HEADER_ISSUER, "http://localhost:1081/auth/realms/default");
      httpHeaders.set(Constants.HEADER_CLIENT_ID, addIdSuffix("stargate", baseSteps.getId()));
      httpHeaders.set(Constants.HEADER_CLIENT_SECRET, "secret");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getProxyRouteHeadersLegacyIssuerWithNonDefaultRealm(
      BaseSteps baseSteps) {
    return httpHeaders -> {
      httpHeaders.setBearerAuth(baseSteps.getAuthHeader());
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(
          Constants.HEADER_ISSUER, "http://localhost:1081/auth/realms/" + NON_DEFAULT_REALM);
      httpHeaders.set(Constants.HEADER_CLIENT_ID, addIdSuffix("stargate", baseSteps.getId()));
      httpHeaders.set(Constants.HEADER_CLIENT_SECRET, "secret");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getRealRouteHeaders() {
    return getRealRouteHeaders(null, false);
  }

  public static Consumer<HttpHeaders> getRealRouteHeaders(String authorization, boolean tls) {
    String remoteApiUrl = tls ? "https://localhost:1080" : "http://localhost:1080";
    return httpHeaders -> {
      if (authorization != null) httpHeaders.setBearerAuth(authorization);
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, remoteApiUrl);
      httpHeaders.set(Constants.HEADER_API_BASE_PATH, BASE_PATH);
      httpHeaders.set(Constants.HEADER_ENVIRONMENT, ENVIRONMENT);
      httpHeaders.set(Constants.HEADER_REALM, REALM);
      httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getRealRouteHeadersWithXtokenExchange(String authorization) {
    return httpHeaders -> {
      if (authorization != null) httpHeaders.setBearerAuth(authorization);
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, "http://localhost:1080");
      httpHeaders.set(Constants.HEADER_API_BASE_PATH, BASE_PATH);
      httpHeaders.set(Constants.HEADER_ENVIRONMENT, ENVIRONMENT);
      httpHeaders.set(Constants.HEADER_REALM, REALM);
      httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
      httpHeaders.set(Constants.HEADER_X_TOKEN_EXCHANGE, "Bearer XTokenExchangeHeader");
    };
  }

  public static Consumer<HttpHeaders> getRealRouteHeadersWithoutRemoteApiUrl(String authorization) {
    return httpHeaders -> {
      if (authorization != null) httpHeaders.setBearerAuth(authorization);
      httpHeaders.set(Constants.HEADER_API_BASE_PATH, BASE_PATH);
      httpHeaders.set(Constants.HEADER_ENVIRONMENT, ENVIRONMENT);
      httpHeaders.set(Constants.HEADER_REALM, REALM);
      httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getRealRouteHeadersWithConflictingRemoteApiUrl(
      String authorization) {
    return httpHeaders -> {
      if (authorization != null) httpHeaders.setBearerAuth(authorization);
      httpHeaders.set(Constants.HEADER_REMOTE_API_URL, REMOTE_HOST + REMOTE_CONFLICTING_BASE_PATH);
      httpHeaders.set(Constants.HEADER_API_BASE_PATH, BASE_PATH);
      httpHeaders.set(Constants.HEADER_ENVIRONMENT, ENVIRONMENT);
      httpHeaders.set(Constants.HEADER_REALM, REALM);
      httpHeaders.set(Constants.HEADER_ACCESS_TOKEN_FORWARDING, "false");
      httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, "e30=");
    };
  }

  public static Consumer<HttpHeaders> getEmptyHeaders() {
    return httpHeaders -> {};
  }
}

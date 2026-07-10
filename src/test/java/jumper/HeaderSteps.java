// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static jumper.config.Config.*;
import static jumper.util.JumperConfigUtil.addIdSuffix;
import static jumper.util.TokenUtil.getConsumerAccessTokenWithAud;
import static jumper.util.TokenUtil.getConsumerAccessTokenWithMultipleAud;

import io.cucumber.java.ParameterType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import jumper.util.RoutingConfigUtil;
import jumper.util.TokenUtil;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HeaderSteps {
  private final BaseSteps baseSteps;

  @ParameterType(value = "true|True|TRUE|false|False|FALSE")
  public Boolean booleanValue(String value) {
    return Boolean.valueOf(value);
  }

  @Given("ProxyRoute headers are set")
  public void proxyRouteHeadersSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getProxyRouteHeaders(baseSteps));
  }

  @Given("ProxyRoute headers are set with x-token-exchange")
  public void proxyRouteHeadersSetWithXtokenExchange() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getProxyRouteHeadersWithXtokenExchange(baseSteps));
  }

  @Given("ProxyRoute headers are set with legacy issuer")
  public void proxyRouteHeadersSetWithLegacyIssuer() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getProxyRouteHeadersLegacyIssuer(baseSteps));
  }

  @Given("ProxyRoute headers are set with jumper_config realm")
  public void proxyRouteHeadersSetWithJumperConfigRealm() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getProxyRouteHeadersWithJumperConfigRealm(baseSteps));
  }

  @Given("ProxyRoute headers are set with legacy realm header")
  public void proxyRouteHeadersSetWithLegacyRealmHeader() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getProxyRouteHeadersWithLegacyRealmHeader(baseSteps));
  }

  @Given("ProxyRoute headers are set with legacy issuer for non-default realm")
  public void proxyRouteHeadersSetWithLegacyIssuerForNonDefaultRealm() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getProxyRouteHeadersLegacyIssuerWithNonDefaultRealm(baseSteps));
  }

  @Given("A header {word} is set with value {word}")
  public void tardisTraceIdSet(String headerName, String headerValue) {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> httpHeaders.add(headerName, headerValue)));
  }

  @Given("RealRoute headers are set")
  public void realRouteHeadersSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getRealRouteHeaders(baseSteps.authHeader, false));
  }

  @Given("RealRoute headers are set with RemoteApiUrl over TLS {booleanValue}")
  public void realRouteHeadersSet(boolean tls) {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getRealRouteHeaders(baseSteps.authHeader, tls));
  }

  @Given("request header {word} is set to {word}")
  public void specificRequestHeaderSet(String header, String value) {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.set(header, value);
            }));
  }

  @Given("RealRoute headers are set with x-token-exchange")
  public void realRouteHeadersSetWithXtokenExchange() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getRealRouteHeadersWithXtokenExchange(baseSteps.authHeader));
  }

  @Given("RealRoute headers without remoteApiUrl are set")
  public void realRouteHeadersWithoutRemoteApiUrlSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getRealRouteHeadersWithoutRemoteApiUrl(baseSteps.authHeader));
  }

  @Given("RealRoute headers with conflicting remoteApiUrl are set")
  public void realRouteHeadersWithConflictingRemoteApiUrlSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        TokenUtil.getRealRouteHeadersWithConflictingRemoteApiUrl(baseSteps.authHeader));
  }

  @Given("Secondary routing_config header set")
  public void secondaryRoutingConfigHeaderSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(RoutingConfigUtil.getSecondaryRouteHeaders(baseSteps));
  }

  @Given("Secondary routing_config with loadbalancing header set")
  public void secondaryRoutingConfigWithLoadbalancingHeaderSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getSecondaryRouteHeadersWithLoadbalancing(baseSteps));
  }

  @Given("Secondary routing_config header set with conflicting remoteApiUrl")
  public void secondaryRoutingConfigHeaderSetWithConflictingRemoteApiUrl() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getSecondaryRouteHeadersWithConflictingRemoteApiUrl(baseSteps));
  }

  @Given("Proxy routing_config header set")
  public void proxyRoutingConfigHeaderSet() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(RoutingConfigUtil.getProxyRouteHeaders(baseSteps));
  }

  @Given("Proxy routing_config header set with consumer route listener")
  public void proxyRoutingConfigHeaderSetWithConsumerRouteListener() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getProxyRouteHeadersWithConsumerRouteListener(baseSteps)
            .andThen(
                httpHeaders -> {
                  httpHeaders.set(Constants.HEADER_X_B3_TRACE_ID, baseSteps.getId());
                  httpHeaders.set(Constants.HEADER_X_B3_SPAN_ID, baseSteps.getSpanId());
                  httpHeaders.set(Constants.HEADER_X_B3_SAMPLED, "1");
                }));
  }

  @Given("Proxy routing_config header set with legacy issuer")
  public void proxyRoutingConfigHeaderSetWithLegacyIssuer() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getProxyRouteHeadersLegacyIssuer(baseSteps));
  }

  @Given("Proxy routing_config header set with routing_config realm")
  public void proxyRoutingConfigHeaderSetWithRoutingConfigRealm() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getProxyRouteHeadersWithRoutingConfigRealm(baseSteps));
  }

  @Given("Proxy routing_config header set with legacy realm header")
  public void proxyRoutingConfigHeaderSetWithLegacyRealmHeader() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getProxyRouteHeadersWithLegacyRealmHeader(baseSteps));
  }

  @Given("Proxy routing_config header set with legacy issuer for non-default realm")
  public void proxyRoutingConfigHeaderSetWithLegacyIssuerForNonDefaultRealm() {
    baseSteps.authHeader = TokenUtil.getConsumerAccessToken();
    baseSteps.setHttpHeadersOfRequest(
        RoutingConfigUtil.getProxyRouteHeadersLegacyIssuerWithNonDefaultRealm(baseSteps));
  }

  @Given("RealRoute headers without Authorization are set")
  public void realRouteHeadersNoAuth() {
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getRealRouteHeaders());
  }

  @Given("No headers are set")
  public void noHeadersSet() {
    baseSteps.setHttpHeadersOfRequest(TokenUtil.getEmptyHeaders());
  }

  @And("pub sub contained in the header")
  public void addPubSubInfoToHeader() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.add(Constants.HEADER_X_PUBSUB_PUBLISHER_ID, PUBSUB_PUBLISHER);
              httpHeaders.add(Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID, PUBSUB_SUBSCRIBER);
            }));
  }

  @And("spacegate oauth headers set")
  public void setSpacegateOauthHeaders() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.set(
                  Constants.HEADER_X_SPACEGATE_CLIENT_ID,
                  addIdSuffix(CONSUMER_EXTERNAL_HEADER, baseSteps.getId()));
              httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_SECRET, "secret");
            }));
  }

  @And("spacegate oauth scoped headers set")
  public void setSpacegateOauthScopedHeaders() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.set(
                  Constants.HEADER_X_SPACEGATE_CLIENT_ID,
                  addIdSuffix(CONSUMER_EXTERNAL_HEADER, baseSteps.getId()));
              httpHeaders.set(Constants.HEADER_X_SPACEGATE_CLIENT_SECRET, "secret");
              httpHeaders.set(Constants.HEADER_X_SPACEGATE_SCOPE, OAUTH_SCOPE_HEADER);
            }));
  }

  @And("authorization token with aud set")
  public void setAuthorizationWithAud() {
    baseSteps.authHeader = getConsumerAccessTokenWithAud();
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> httpHeaders.setBearerAuth(baseSteps.authHeader)));
  }

  @And("authorization token with multiple aud set")
  public void setAuthorizationWithMultipleAud() {
    baseSteps.authHeader = getConsumerAccessTokenWithMultipleAud();
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> httpHeaders.setBearerAuth(baseSteps.authHeader)));
  }

  @And("authorization token originates from internet-facing zone {string}")
  public void setAuthorizationFromInternetFacingZone(String zone) {
    baseSteps.authHeader = TokenUtil.getConsumerAccessTokenFromZone(zone);
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> httpHeaders.setBearerAuth(baseSteps.authHeader)));
  }

  @And("technical headers added")
  public void addTechnicalHeaders() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.add("x-consumer-id", "dummy");
              httpHeaders.add("x-consumer-custom-id", "dummy");
              httpHeaders.add("x-consumer-groups", "dummy");
              httpHeaders.add("x-consumer-username", "dummy");
              httpHeaders.add("x-anonymous-consumer", "dummy");
              httpHeaders.add("x-anonymous-groups", "dummy");
              httpHeaders.add("x-forwarded-prefix", "dummy");
            }));
  }

  @And("skip zone header set")
  public void setSkipZoneHeader() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders ->
                httpHeaders.set(Constants.HEADER_X_FAILOVER_SKIP_ZONE, REMOTE_ZONE_NAME)));
  }
}

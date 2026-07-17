// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static jumper.config.Config.*;
import static jumper.util.JumperConfigUtil.addIdSuffix;
import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.en.Then;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;
import jumper.util.OauthTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;

@RequiredArgsConstructor
public class VerificationSteps {
  private final BaseSteps baseSteps;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Then("API Provider receives default bearer authorization headers")
  public void apiProvidersReceivesDefaultTokenHeaders() {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(
            HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\S+").pattern());
    apiProvidersReceivesDefaultHeaders();
  }

  @Then("API Provider receives header {word} that matches regex {word}")
  public void apiProviderReceivesXTardisTraceId(String headerName, String valueRegex) {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(headerName, Pattern.compile("\\w+").pattern());
  }

  @Then("API Provider receives default basic authorization headers")
  public void apiProvidersReceivesDefaultBasicAuthHeaders() {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(HttpHeaders.AUTHORIZATION, Pattern.compile("Basic\\s\\w+=*").pattern());
    apiProvidersReceivesDefaultHeaders();
  }

  @Then("API Provider receives no technical headers")
  public void apiProvidersReceivesNoTechnicalHeaders() {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .doesNotExist("jumper_config")
        .expectHeader()
        .doesNotExist("token_endpoint")
        .expectHeader()
        .doesNotExist("remote_api_url")
        .expectHeader()
        .doesNotExist("issuer")
        .expectHeader()
        .doesNotExist("client_id")
        .expectHeader()
        .doesNotExist("client_secret")
        .expectHeader()
        .doesNotExist("api_base_path")
        .expectHeader()
        .doesNotExist("x-consumer-id")
        .expectHeader()
        .doesNotExist("x-consumer-custom-id")
        .expectHeader()
        .doesNotExist("x-consumer-groups")
        .expectHeader()
        .doesNotExist("x-consumer-username")
        .expectHeader()
        .doesNotExist("x-anonymous-consumer")
        .expectHeader()
        .doesNotExist("x-anonymous-groups")
        .expectHeader()
        .doesNotExist("x-forwarded-prefix")
        .expectHeader()
        .doesNotExist("access_token_forwarding");
  }

  @Then("API Provider receives no failover headers")
  public void apiProvidersReceivesNoFailoverHeaders() {
    this.baseSteps.getRequestExchange().expectHeader().doesNotExist("routing_config");
  }

  @Then("API Provider receives documented standard and consumer headers")
  public void apiProviderReceivesDocumentedHeaders() {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_TRACE_ID, "\\w+")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_SPAN_ID, "\\w+")
        .expectHeader()
        .valueEquals(Constants.HEADER_X_B3_SAMPLED, "1")
        .expectHeader()
        .valueEquals(Constants.HEADER_X_FORWARDED_HOST, "zone.local.de")
        .expectHeader()
        .valueEquals(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
        .expectHeader()
        .valueEquals(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS)
        .expectHeader()
        .valueEquals(Constants.HEADER_X_FORWARDED_FOR, FORWARDED_FOR)
        .expectHeader()
        .valueEquals(Constants.HEADER_X_FORWARDED_PATH, FORWARDED_PATH)
        .expectHeader()
        .valueEquals(Constants.HEADER_REALM, REALM)
        .expectHeader()
        .valueEquals(Constants.HEADER_ENVIRONMENT, ENVIRONMENT)
        .expectHeader()
        .valueEquals(CUSTOM_CONSUMER_HEADER, CUSTOM_CONSUMER_HEADER_VALUE);
  }

  @Then("API Provider receives no authorization header")
  public void apiProvidersReceivesNoAuthorizationHeader() {
    this.baseSteps.getRequestExchange().expectHeader().doesNotExist("Authorization");
  }

  @Then("API Provider receives authorization {word}")
  public void apiProviderReceivesToken(String tokenType) {
    if (tokenType.equalsIgnoreCase("OneToken")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken);
    } else if (tokenType.equalsIgnoreCase("OneTokenSimple")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneTokenSimple);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithPubSub")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkPubSub);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithScopes")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkScopes);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkAud);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithMultipleAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkMultipleAud);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithConfiguredAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkConfiguredAud);
    } else if (tokenType.equalsIgnoreCase("OneTokenWithConsumerClientIdAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneToken)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkConsumerClientIdAud);
    } else if (tokenType.equalsIgnoreCase("MeshTokenWithoutConfiguredAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkMeshTokenIgnoresConfiguredAud)
          .expectHeader()
          .doesNotExist(Constants.HEADER_CONSUMER_TOKEN);
    } else if (tokenType.equalsIgnoreCase("OneTokenSimpleWithConfiguredAud")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkOneTokenSimple)
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkConfiguredAud);
    } else if (tokenType.equalsIgnoreCase("MeshToken")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkMeshToken)
          .expectHeader()
          .doesNotExist(Constants.HEADER_CONSUMER_TOKEN);
    } else if (tokenType.equalsIgnoreCase("MeshTokenWithNonDefaultRealm")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkMeshTokenWithNonDefaultRealm)
          .expectHeader()
          .doesNotExist(Constants.HEADER_CONSUMER_TOKEN);
    } else if (tokenType.equalsIgnoreCase("ExternalConfigured")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkExternalConfigured);
    } else if (tokenType.equalsIgnoreCase("AlternativeClient")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkAlternativeClient);
    } else if (tokenType.equalsIgnoreCase("ExternalHeader")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkExternalHeader);
    } else if (tokenType.equalsIgnoreCase("BasicAuthConsumer")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkBasicAuthConsumer);
    } else if (tokenType.equalsIgnoreCase("BasicAuthProvider")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkBasicAuthProvider);
    } else if (tokenType.equalsIgnoreCase("XTokenExchangeHeader")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .valueMatches(HttpHeaders.AUTHORIZATION, "Bearer XTokenExchangeHeader");
    } else {
      fail("unknown authorization received");
    }
  }

  private void apiProvidersReceivesDefaultHeaders() {
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, ORIGIN_STARGATE)
        .expectHeader()
        .valueMatches(Constants.HEADER_X_ORIGIN_ZONE, ORIGIN_ZONE)
        .expectHeader()
        .valueMatches(Constants.HEADER_X_FORWARDED_HOST, "zone.local.de")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
        .expectHeader()
        .valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);
  }

  private void checkOneToken(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals("Bearer", claimsFromToken.getBody().get("typ", String.class));
    assertEquals(CONSUMER, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals("stargate", claimsFromToken.getBody().get("azp", String.class));
    assertEquals(ENVIRONMENT, claimsFromToken.getBody().get("env", String.class));
    assertEquals("GET", claimsFromToken.getBody().get("operation", String.class));
    assertEquals(
        BASE_PATH + CALLBACK_SUFFIX, claimsFromToken.getBody().get("requestPath", String.class));
    assertEquals(ORIGIN_ZONE, claimsFromToken.getBody().get("originZone", String.class));
    assertEquals(ORIGIN_STARGATE, claimsFromToken.getBody().get("originStargate", String.class));
    assertEquals(
        localIssuerUrl + "/" + Constants.DEFAULT_REALM, claimsFromToken.getBody().getIssuer());
    assertNotNull(claimsFromToken.getBody().getExpiration());
    assertNotNull(claimsFromToken.getBody().getIssuedAt());
  }

  private void checkOneTokenSimple(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals("Bearer", claimsFromToken.getBody().get("typ", String.class));
    assertEquals(CONSUMER, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals("stargate", claimsFromToken.getBody().get("azp", String.class));
    assertEquals(ENVIRONMENT, claimsFromToken.getBody().get("env", String.class));
    assertEquals("GET", claimsFromToken.getBody().get("operation", String.class));
    assertEquals(ORIGIN_ZONE, claimsFromToken.getBody().get("originZone", String.class));
    assertEquals(ORIGIN_STARGATE, claimsFromToken.getBody().get("originStargate", String.class));
    assertEquals(
        localIssuerUrl + "/" + Constants.DEFAULT_REALM, claimsFromToken.getBody().getIssuer());
    assertNotNull(claimsFromToken.getBody().getExpiration());
    assertNotNull(claimsFromToken.getBody().getIssuedAt());
  }

  private void checkPubSub(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals(PUBSUB_PUBLISHER, claimsFromToken.getBody().get("publisherId", String.class));
    assertEquals(PUBSUB_SUBSCRIBER, claimsFromToken.getBody().get("subscriberId", String.class));
    assertEquals(Set.of(PUBSUB_SUBSCRIBER), claimsFromToken.getBody().getAudience());
  }

  private void checkScopes(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals(SCOPES, claimsFromToken.getBody().get("scope", String.class));
  }

  private void checkAud(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals(Set.of("testAudience"), claimsFromToken.getBody().getAudience());
  }

  private void checkMultipleAud(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals(Set.of("testAudience1", "testAudience2"), claimsFromToken.getBody().getAudience());
  }

  // aud configured via jumper_config claims (DHEI-21196); azp pins the provider LMS token
  private void checkConfiguredAud(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals("stargate", claimsFromToken.getBody().get("azp", String.class));
    assertEquals(Set.of(CONFIGURED_AUDIENCE), claimsFromToken.getBody().getAudience());
  }

  private void checkConsumerClientIdAud(String providerLmsToken) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(providerLmsToken);

    assertEquals("stargate", claimsFromToken.getBody().get("azp", String.class));
    assertEquals(Set.of(CONSUMER), claimsFromToken.getBody().getAudience());
  }

  private void checkMeshToken(String meshLmsToken) {
    checkMeshToken(meshLmsToken, Constants.DEFAULT_REALM);
  }

  // pins the DHEI-21196 gate end-to-end: configured claims must not leak onto the mesh LMS
  // token (the consumer token in this scenario carries no aud, so any aud here is a leak)
  private void checkMeshTokenIgnoresConfiguredAud(String meshLmsToken) {
    checkMeshToken(meshLmsToken);
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(meshLmsToken);

    assertNull(claimsFromToken.getBody().getAudience());
  }

  private void checkMeshTokenWithNonDefaultRealm(String meshLmsToken) {
    checkMeshToken(meshLmsToken, NON_DEFAULT_REALM);
  }

  private void checkMeshToken(String meshLmsToken, String expectedRealm) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(meshLmsToken);

    assertEquals("Bearer", claimsFromToken.getBody().get("typ", String.class));
    // Mesh LMS token carries the real consumer identity, not the "gateway" client
    assertEquals(CONSUMER, claimsFromToken.getBody().get("clientId", String.class));
    // azp is "gateway" so the provider-zone ACL group check passes
    assertEquals(CONSUMER_GATEWAY, claimsFromToken.getBody().get("azp", String.class));
    // env is absent: proxy route headers (TokenUtil.getProxyRouteHeaders) do not include
    // `environment`. The null-guard in generateLmsToken ensures the claim is omitted rather
    // than written as null.
    assertNull(claimsFromToken.getBody().get("env", String.class));
    assertEquals("GET", claimsFromToken.getBody().get("operation", String.class));
    // originZone and originStargate come from the consumer token, not the remote zone
    assertEquals(ORIGIN_ZONE, claimsFromToken.getBody().get("originZone", String.class));
    assertEquals(ORIGIN_STARGATE, claimsFromToken.getBody().get("originStargate", String.class));
    // iss is the local StarGate issuer URL — the provider zone validates against its JWKS
    assertEquals(localIssuerUrl + "/" + expectedRealm, claimsFromToken.getBody().getIssuer());
    assertNotNull(claimsFromToken.getBody().getExpiration());
    assertNotNull(claimsFromToken.getBody().getIssuedAt());
  }

  private void checkExternalConfigured(String token) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(token);

    assertEquals(
        CONSUMER_EXTERNAL_CONFIGURED, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
  }

  private void checkAlternativeClient(String token) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(token);

    assertEquals("alternative_client", claimsFromToken.getBody().get("clientId", String.class));
    assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
  }

  private void checkExternalHeader(String token) {
    Jwt<?, Claims> claimsFromToken = OauthTokenUtil.getAllClaimsFromToken(token);

    assertEquals(CONSUMER_EXTERNAL_HEADER, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
  }

  private void checkBasicAuthConsumer(String basicAuthEncoded) {
    String basicAuthDecoded =
        new String(
            Base64.getDecoder().decode(basicAuthEncoded.replaceFirst("Basic ", "").getBytes()));
    String[] basicAuthSplitted = basicAuthDecoded.split(":");
    assertEquals(addIdSuffix(CONSUMER, this.baseSteps.getId()), basicAuthSplitted[0]);
    assertEquals("password", basicAuthSplitted[1]);
  }

  private void checkBasicAuthProvider(String basicAuthEncoded) {
    String basicAuthDecoded =
        new String(
            Base64.getDecoder().decode(basicAuthEncoded.replaceFirst("Basic ", "").getBytes()));
    String[] basicAuthSplitted = basicAuthDecoded.split(":");
    assertEquals(addIdSuffix(CONSUMER_GATEWAY, this.baseSteps.getId()), basicAuthSplitted[0]);
    assertEquals("geheim", basicAuthSplitted[1]);
  }
}

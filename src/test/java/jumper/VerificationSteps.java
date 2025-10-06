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
    } else if (tokenType.equalsIgnoreCase("MeshToken")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkMeshToken)
          .expectHeader()
          .valueMatches(Constants.HEADER_CONSUMER_TOKEN, "Bearer " + baseSteps.authHeader);
    } else if (tokenType.equalsIgnoreCase("ExternalConfigured")) {
      this.baseSteps
          .getRequestExchange()
          .expectHeader()
          .value(HttpHeaders.AUTHORIZATION, this::checkExternalConfigured);
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
        .valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
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

  private void checkOneToken(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

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

  private void checkOneTokenSimple(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

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

  private void checkPubSub(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

    assertEquals(PUBSUB_PUBLISHER, claimsFromToken.getBody().get("publisherId", String.class));
    assertEquals(PUBSUB_SUBSCRIBER, claimsFromToken.getBody().get("subscriberId", String.class));
    assertEquals(PUBSUB_SUBSCRIBER, claimsFromToken.getBody().get("aud", String.class));
  }

  private void checkScopes(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

    assertEquals(SCOPES, claimsFromToken.getBody().get("scope", String.class));
  }

  private void checkAud(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

    assertEquals("testAudience", claimsFromToken.getBody().get("aud", String.class));
  }

  private void checkMeshToken(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

    assertEquals("Bearer", claimsFromToken.getBody().get("typ", String.class));
    assertEquals(CONSUMER_GATEWAY, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals(CONSUMER_GATEWAY, claimsFromToken.getBody().get("azp", String.class));
    assertEquals(ENVIRONMENT_REMOTE, claimsFromToken.getBody().get("env", String.class));
    assertEquals(ORIGIN_ZONE_REMOTE, claimsFromToken.getBody().get("originZone", String.class));
    assertEquals(
        ORIGIN_STARGATE_REMOTE, claimsFromToken.getBody().get("originStargate", String.class));
    assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
    assertNotNull(claimsFromToken.getBody().getExpiration());
    assertNotNull(claimsFromToken.getBody().getIssuedAt());
  }

  private void checkExternalConfigured(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

    assertEquals(
        CONSUMER_EXTERNAL_CONFIGURED, claimsFromToken.getBody().get("clientId", String.class));
    assertEquals(REMOTE_ISSUER, claimsFromToken.getBody().getIssuer());
  }

  private void checkExternalHeader(String token) {
    Jwt<?, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(token));

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

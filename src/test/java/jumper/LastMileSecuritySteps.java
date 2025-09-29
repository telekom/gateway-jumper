// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import jumper.util.OauthTokenUtil;
import jumper.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public class LastMileSecuritySteps {

  private final BaseSteps baseSteps;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  Consumer<HttpHeaders> httpHeadersOfRequest;

  @Given("lastMileSecurity is activated")
  public void lastMileSecurityIsActivated() {
    httpHeadersOfRequest = TokenUtil.getJumperLmsHeaders();
    baseSteps.setHttpHeadersOfRequest(httpHeadersOfRequest);
  }

  @Then("API provider receives {word} and {word}")
  public void apiProviderReceivesAccessTokenAndGatewayToken(String at, String gt) {

    if (!Objects.equals(at, "AccessToken") || !Objects.equals(gt, "GatewayToken")) {
      this.baseSteps.getRequestExchange().expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
      return;
    }
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .valueMatches(
            HttpHeaders.AUTHORIZATION, Pattern.compile("Bearer\\s\\w+.\\w+.+.\\S+").pattern())
        .expectHeader()
        .valueMatches(
            Constants.HEADER_LASTMILE_SECURITY_TOKEN,
            Pattern.compile("Bearer\\s\\w+.\\w+.+.\\S+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_TRACE_ID, Pattern.compile("\\w+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_SPAN_ID, Pattern.compile("\\w+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_PARENT_SPAN_ID, Pattern.compile("\\w+").pattern())
        .expectHeader()
        .valueMatches(Constants.HEADER_X_B3_SAMPLED, "1")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_ORIGIN_STARGATE, "https://zone.local.de")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_ORIGIN_ZONE, "localZone")
        .expectHeader()
        .valueMatches(Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT)
        .expectHeader()
        .valueMatches(Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);

    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .value(HttpHeaders.AUTHORIZATION, this::checkConsumerToken);
    this.baseSteps
        .getRequestExchange()
        .expectHeader()
        .value(Constants.HEADER_LASTMILE_SECURITY_TOKEN, this::checkGatewayToken);
  }

  private void checkConsumerToken(String consumerToken) {
    Jwt<Header, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(
            OauthTokenUtil.getTokenWithoutSignature(consumerToken));

    assertNotNull(claimsFromToken.getBody().get("clientId", String.class));
  }

  private void checkGatewayToken(String gatewayToken) {
    Jwt<Header, Claims> claimsFromToken =
        OauthTokenUtil.getAllClaimsFromToken(OauthTokenUtil.getTokenWithoutSignature(gatewayToken));

    assertEquals(
        localIssuerUrl + "/" + Constants.DEFAULT_REALM,
        claimsFromToken.getBody().get("iss", String.class));
  }
}

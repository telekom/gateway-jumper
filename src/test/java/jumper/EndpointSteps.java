// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import io.cucumber.java.en.And;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EndpointSteps {
  private final BaseSteps baseSteps;

  @And("oauth tokenEndpoint set")
  public void setOauthTokenEnpoint() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.set(Constants.HEADER_TOKEN_ENDPOINT, "http://localhost:1081/external");
            }));
  }
}

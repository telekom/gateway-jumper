// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import static jumper.util.JumperConfigUtil.*;

import io.cucumber.java.en.And;
import jumper.config.Config;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JumperConfigSteps {
  private final BaseSteps baseSteps;

  @And("jumperConfig with {word} route listener set")
  public void setJumperConfigListener(String jc_case) {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              // tracing headers we use for matching on horizon mock
              httpHeaders.set(Constants.HEADER_X_B3_TRACE_ID, baseSteps.getId());
              httpHeaders.set(Constants.HEADER_X_B3_SPAN_ID, baseSteps.getId());

              switch (jc_case) {
                case "consumer":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcRouteListener(baseSteps.getId(), Config.CONSUMER));
                  break;
                case "otherConsumer":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcRouteListener(baseSteps.getId(), Config.CONSUMER_EXTERNAL_CONFIGURED));
                  break;
              }
            }));
  }

  @And("jumperConfig with scopes set")
  public void setJumperConfigScopes() {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcSecurity());
            }));
  }

  @And("jumperConfig oauth {string} set")
  public void setJumperConfigOauth(String jc_case) {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              switch (jc_case) {
                case "grant_type password":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcOauthGrantTypePassword(baseSteps.getId()));
                  break;
                case "grant_type password only":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcOauthGrantTypePasswordOnly(baseSteps.getId()));
                  break;
                case "grant_type client_credentials":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG, getJcOauthGrantType(baseSteps.getId()));
                  break;
                case "scoped":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG, getJcOauthWithScope(baseSteps.getId()));
                  break;
                default:
                  httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauth(baseSteps.getId()));
              }
            }));
  }

  @And("jumperConfig basic auth {string} set")
  public void setJumperConfigBasicAuth(String jc_case) {
    baseSteps.setHttpHeadersOfRequest(
        baseSteps.httpHeadersOfRequest.andThen(
            httpHeaders -> {
              switch (jc_case) {
                case "consumer key only":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthConsumer(baseSteps.getId()));
                  break;
                case "provider key only":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG, getJcBasicAuthProvider(baseSteps.getId()));
                  break;
                case "consumer and provider":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcBasicAuthConsumerAndProvider(baseSteps.getId()));
                  break;
                case "other consumer present":
                  httpHeaders.set(
                      Constants.HEADER_JUMPER_CONFIG,
                      getJcBasicAuthOtherConsumer(baseSteps.getId()));
                  break;
                default:
                  httpHeaders.set(Constants.HEADER_JUMPER_CONFIG, getJcOauth(baseSteps.getId()));
              }
            }));
  }
}

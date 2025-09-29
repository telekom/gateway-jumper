// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import java.util.Optional;
import jumper.Constants;
import jumper.filter.RequestProcessingContext;
import jumper.model.config.BasicAuthCredentials;
import jumper.util.BasicAuthUtil;
import jumper.util.HeaderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for Basic Authentication scenarios. Handles external authorization using
 * username/password credentials.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BasicAuthStrategy implements AuthenticationStrategy {

  @Override
  public void authenticate(RequestProcessingContext context) {
    log.debug("----------------BASIC AUTH HEADER-------------");

    context
        .getJumperInfoRequest()
        .ifPresent(i -> i.setInfoScenario(false, false, false, false, true, false));

    Optional<BasicAuthCredentials> basicAuthCredentials =
        context.getJumperConfig().getBasicAuthCredentials();

    if (basicAuthCredentials.isPresent()) {
      String encodedBasicAuth =
          BasicAuthUtil.encodeBasicAuth(
              basicAuthCredentials.get().getUsername(), basicAuthCredentials.get().getPassword());

      HeaderUtil.addHeader(
          context.getRequestBuilder(),
          Constants.HEADER_AUTHORIZATION,
          Constants.BASIC + " " + encodedBasicAuth);
    }
  }

  @Override
  public boolean canHandle(RequestProcessingContext context) {
    return context.isRemoteHostTarget()
        && context.getJumperConfig().getInternalTokenEndpoint() == null
        && (!context
                .getReadOnlyRequest()
                .getHeaders()
                .containsKey(Constants.HEADER_X_TOKEN_EXCHANGE)
            || !context.isCurrentZoneInternetFacing())
        && context.getJumperConfig().getBasicAuthCredentials().isPresent();
  }

  @Override
  public String getStrategyName() {
    return "BasicAuth";
  }
}

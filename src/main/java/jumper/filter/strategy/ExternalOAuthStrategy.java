// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import java.util.Objects;
import jumper.Constants;
import jumper.filter.RequestProcessingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for external OAuth token scenarios. Handles cases where an external token
 * endpoint is configured and OAuth processing is needed.
 */
@Component
@Slf4j
public class ExternalOAuthStrategy implements AuthenticationStrategy {

  @Override
  public void authenticate(RequestProcessingContext context) {
    log.debug("----------------EXTERNAL OAUTH-------------");

    // Set OAuth filter needed flag - actual token retrieval happens in UpstreamOAuthFilter
    setOAuthFilterNeeded(context);
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
        && context.getJumperConfig().getBasicAuthCredentials().isEmpty()
        && Objects.nonNull(context.getJumperConfig().getExternalTokenEndpoint());
  }

  @Override
  public String getStrategyName() {
    return "ExternalOAuth";
  }

  private void setOAuthFilterNeeded(RequestProcessingContext context) {
    context
        .getExchange()
        .getAttributes()
        .put(Constants.GATEWAY_ATTRIBUTE_OAUTH_FILTER_NEEDED, true);
  }
}

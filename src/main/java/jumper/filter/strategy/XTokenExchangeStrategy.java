// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import jumper.Constants;
import jumper.filter.RequestProcessingContext;
import jumper.util.HeaderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for X-Token-Exchange header scenarios. Handles token exchange when the
 * request contains the X-Token-Exchange header and the current zone is internet-facing.
 */
@Component
@Slf4j
public class XTokenExchangeStrategy implements AuthenticationStrategy {

  @Override
  public void authenticate(RequestProcessingContext context) {
    log.debug("----------------X-TOKEN-EXCHANGE HEADER-------------");

    context
        .getJumperInfoRequest()
        .ifPresent(i -> i.setInfoScenario(false, false, false, false, false, true));

    addXTokenExchange(context);
  }

  @Override
  public boolean canHandle(RequestProcessingContext context) {
    return context.isRemoteHostTarget()
        && context.getJumperConfig().getInternalTokenEndpoint() == null
        && context.getReadOnlyRequest().getHeaders().containsKey(Constants.HEADER_X_TOKEN_EXCHANGE)
        && context.isCurrentZoneInternetFacing();
  }

  @Override
  public String getStrategyName() {
    return "XTokenExchange";
  }

  private void addXTokenExchange(RequestProcessingContext context) {
    HeaderUtil.addHeader(
        context.getRequestBuilder(),
        Constants.HEADER_AUTHORIZATION,
        HeaderUtil.getFirstValueFromHeaderField(
            context.getReadOnlyRequest(), Constants.HEADER_X_TOKEN_EXCHANGE));

    log.debug(
        "x-token-exchange: "
            + HeaderUtil.getFirstValueFromHeaderField(
                context.getReadOnlyRequest(), Constants.HEADER_X_TOKEN_EXCHANGE));
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import java.util.Objects;
import jumper.Constants;
import jumper.filter.RequestProcessingContext;
import jumper.util.HeaderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for Gateway-to-Gateway mesh token generation. Handles internal token
 * endpoint scenarios where gateways communicate with each other.
 */
@Component
@Slf4j
public class MeshTokenStrategy implements AuthenticationStrategy {

  @Override
  public void authenticate(RequestProcessingContext context) {
    log.debug("----------------GATEWAY MESH-------------");

    context
        .getJumperInfoRequest()
        .ifPresent(i -> i.setInfoScenario(false, false, true, false, false, false));

    HeaderUtil.addHeader(
        context.getRequestBuilder(),
        Constants.HEADER_CONSUMER_TOKEN,
        context.getJumperConfig().getConsumerToken());

    checkForInternetFacingZone(context);
    setOAuthFilterNeeded(context, true);
  }

  @Override
  public boolean canHandle(RequestProcessingContext context) {
    return context.isRemoteHostTarget()
        && Objects.nonNull(context.getJumperConfig().getInternalTokenEndpoint());
  }

  @Override
  public String getStrategyName() {
    return "MeshToken";
  }

  private void checkForInternetFacingZone(RequestProcessingContext context) {
    String zone = context.getJumperConfig().getConsumerOriginZone();
    String token = context.getJumperConfig().getConsumerToken();

    if (context.isInternetFacingZone(zone)) {
      HeaderUtil.addHeader(context.getRequestBuilder(), Constants.HEADER_X_SPACEGATE_TOKEN, token);
    }
  }

  private void setOAuthFilterNeeded(RequestProcessingContext context, boolean isNeeded) {
    context
        .getExchange()
        .getAttributes()
        .put(Constants.GATEWAY_ATTRIBUTE_OAUTH_FILTER_NEEDED, isNeeded);
  }
}

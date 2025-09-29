// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import java.util.Objects;
import jumper.Constants;
import jumper.filter.RequestProcessingContext;
import jumper.service.OauthTokenUtil;
import jumper.util.HeaderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for Enhanced Last Mile Security Token scenarios. Handles cases where no
 * external token endpoint is configured and generates a last mile security token for the request.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LastMileSecurityStrategy implements AuthenticationStrategy {

  private final OauthTokenUtil oauthTokenUtil;

  @Override
  public void authenticate(RequestProcessingContext context) {
    log.debug("----------------LAST MILE SECURITY (ONE TOKEN)-------------");

    context
        .getJumperInfoRequest()
        .ifPresent(i -> i.setInfoScenario(true, true, false, false, false, false));

    String enhancedLastmileSecurityToken =
        oauthTokenUtil.generateEnhancedLastMileGatewayToken(
            context.getJumperConfig(),
            String.valueOf(context.getReadOnlyRequest().getMethod()),
            context.getLocalIssuerUrl() + "/" + context.getJumperConfig().getRealmName(),
            HeaderUtil.getLastValueFromHeaderField(
                context.getReadOnlyRequest(), Constants.HEADER_X_PUBSUB_PUBLISHER_ID),
            HeaderUtil.getLastValueFromHeaderField(
                context.getReadOnlyRequest(), Constants.HEADER_X_PUBSUB_SUBSCRIBER_ID),
            false);

    HeaderUtil.addHeader(
        context.getRequestBuilder(),
        Constants.HEADER_AUTHORIZATION,
        Constants.BEARER + " " + enhancedLastmileSecurityToken);

    log.debug("lastMileSecurityToken: " + enhancedLastmileSecurityToken);
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
        && Objects.isNull(context.getJumperConfig().getExternalTokenEndpoint());
  }

  @Override
  public String getStrategyName() {
    return "LastMileSecurity";
  }
}

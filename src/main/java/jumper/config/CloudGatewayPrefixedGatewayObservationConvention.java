// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import org.springframework.cloud.gateway.filter.headers.observation.DefaultGatewayObservationConvention;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class CloudGatewayPrefixedGatewayObservationConvention
    extends DefaultGatewayObservationConvention {

  public static final String NAME = "spring.cloud.gateway.http.client.requests";

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }
}

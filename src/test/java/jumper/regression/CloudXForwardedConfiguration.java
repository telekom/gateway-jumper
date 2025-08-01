// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.regression;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class CloudXForwardedConfiguration {

  @Bean
  public RouteLocator customRouteLocator(
      RouteLocatorBuilder builder, @Value("${mockserver.port}") String port) {
    return builder
        .routes()
        .route("sample", r -> r.path("/get").uri("http://localhost:" + port))
        .build();
  }
}

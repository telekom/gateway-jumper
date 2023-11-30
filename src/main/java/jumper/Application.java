// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jumper.filter.RemoveRequestHeaderFilter;
import jumper.filter.RequestFilter;
import jumper.filter.RequestTransformationFilter;
import jumper.filter.ResponseFilter;
import jumper.filter.ResponseTransformationFilter;
import jumper.filter.SpectreRequestFilter;
import jumper.filter.SpectreResponseFilter;
import jumper.filter.SpectreRoutingFilter;
import jumper.filter.rewrite.SpectreBodyRewrite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;

@SpringBootApplication
public class Application {

  @Value("${horizon.publishEventUrl}")
  private String publishEventUrl;

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public RouteLocator proxyRoute(
      RouteLocatorBuilder builder,
      RequestFilter requestFilter,
      RemoveRequestHeaderFilter removeRequestHeader,
      ResponseFilter responseFilter,
      SpectreRequestFilter spectreRequestFilter,
      SpectreResponseFilter spectreResponseFilter,
      RequestTransformationFilter requestTransformationFilter,
      ResponseTransformationFilter responseTransformationFilter,
      SpectreRoutingFilter spectreRoutingFilter,
      SpectreBodyRewrite spectreBodyRewrite) {

    Set<String> headerRemovalList =
        new HashSet<>(
            Arrays.asList(
                Constants.HEADER_JUMPER_CONFIG,
                Constants.HEADER_TOKEN_ENDPOINT,
                Constants.HEADER_REMOTE_API_URL,
                Constants.HEADER_ISSUER,
                Constants.HEADER_CLIENT_ID,
                Constants.HEADER_CLIENT_SECRET,
                Constants.HEADER_API_BASE_PATH,
                "x-consumer-id",
                "x-consumer-custom-id",
                "x-consumer-groups",
                "x-consumer-username",
                "x-anonymous-consumer",
                "x-anonymous-groups",
                "x-forwarded-prefix",
                Constants.HEADER_ACCESS_TOKEN_FORWARDING));

    return builder
        .routes()
        .route(
            "jumper_route",
            p ->
                p.path(Constants.PROXY_ROOT_PATH_PREFIX + "/**")
                    .filters(
                        filterSpec ->
                            filterSpec
                                .filter(
                                    requestFilter.apply(
                                        new RequestFilter.Config(Constants.PROXY_ROOT_PATH_PREFIX)))
                                .filter(
                                    removeRequestHeader.apply(
                                        config -> config.setHeaders(headerRemovalList)))
                                .filter(responseFilter.apply(config -> {})))
                    .uri("no://op"))
        .route(
            "listener_route",
            p ->
                p.path(Constants.LISTENER_ROOT_PATH_PREFIX + "/**")
                    .filters(
                        filterSpec ->
                            filterSpec
                                .filter(
                                    requestFilter.apply(
                                        new RequestFilter.Config(
                                            Constants.LISTENER_ROOT_PATH_PREFIX)))
                                .filter(
                                    removeRequestHeader.apply(
                                        config -> config.setHeaders(headerRemovalList)))
                                .filter(requestTransformationFilter)
                                .filter(spectreRequestFilter.apply(config -> {}))
                                .filter(responseFilter.apply(config -> {}))
                                .filter(responseTransformationFilter)
                                .filter(spectreResponseFilter.apply(config -> {})))
                    .uri("no://op"))
        .route(
            "auto_event_route_post",
            p ->
                p.path(Constants.AUTOEVENT_ROOT_PATH_PREFIX + "/**")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filterSpec ->
                            filterSpec
                                .modifyRequestBody(String.class, String.class, spectreBodyRewrite)
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(spectreRoutingFilter.apply()))
                    .uri(publishEventUrl))
        .route(
            "auto_event_route_head",
            p ->
                p.path(Constants.AUTOEVENT_ROOT_PATH_PREFIX + "/**")
                    .and()
                    .method(HttpMethod.HEAD)
                    .filters(
                        filterSpec ->
                            filterSpec
                                .removeRequestParameter(Constants.QUERY_PARAM_LISTENER)
                                .filter(spectreRoutingFilter.apply()))
                    .uri(publishEventUrl))
        .build();
  }
}

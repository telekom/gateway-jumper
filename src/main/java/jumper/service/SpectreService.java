// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jumper.Constants;
import jumper.config.SpectreConfiguration;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreData;
import jumper.model.config.SpectreKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpectreService {

  private final OauthTokenUtil oauthTokenUtil;
  private final Tracer tracer;

  @Qualifier("spectreServiceWebClient")
  private final WebClient spectreServiceWebClient;

  @Value("${jumper.stargate.url}")
  private String stargateUrl;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${jumper.horizon.publishEventUrl}")
  private String publishEventUrl;

  @Autowired private SpectreConfiguration spectreConfiguration;

  public Mono<Void> handleEvent(
      JumperConfig jc,
      ServerWebExchange exchange,
      Object http,
      RouteListener listener,
      String payload) {
    return publishEvent(createEvent(jc, exchange, http, listener, payload), jc);
  }

  private Spectre createEvent(
      JumperConfig jc,
      ServerWebExchange exchange,
      Object http,
      RouteListener listener,
      String payload) {

    ServerHttpRequest rq = exchange.getRequest();
    ServerHttpResponse rs = exchange.getResponse();

    SpectreData data = new SpectreData();
    String spanName = "Spectre request";

    if (http instanceof ServerHttpRequest) {
      Map<String, String> httpHeaders = new HashMap<>(rq.getHeaders().toSingleValueMap());
      httpHeaders.replace(Constants.HEADER_AUTHORIZATION, jc.getConsumerToken());
      httpHeaders.remove(Constants.HEADER_CONSUMER_TOKEN);
      data.setHeader(httpHeaders);
      data.setKind(SpectreKind.REQUEST.toString());
      data.setPayload(parsePayload(rq.getHeaders().getContentType(), payload));
      data.setParameters(rq.getQueryParams().toSingleValueMap());

    } else if (http instanceof ServerHttpResponse) {
      spanName = ("Spectre response");

      Map<String, String> httpHeaders = new HashMap<>(rs.getHeaders().toSingleValueMap());
      httpHeaders.put(
          Constants.HEADER_X_TARDIS_TRACE_ID,
          rq.getHeaders().getFirst(Constants.HEADER_X_TARDIS_TRACE_ID));
      data.setHeader(httpHeaders);
      data.setKind(SpectreKind.RESPONSE.toString());
      data.setPayload(parsePayload(rs.getHeaders().getContentType(), payload));
      data.setStatus(Objects.requireNonNull(rs.getStatusCode()).value());
    }

    data.setConsumer(jc.getConsumer());
    data.setIssue(listener.getIssue());
    data.setProvider(listener.getServiceOwner());
    data.setMethod(Objects.requireNonNull(rq.getMethod()).toString());

    Spectre event =
        Spectre.builder()
            .specversion("1.0")
            .source(stargateUrl)
            .id(UUID.randomUUID())
            .datacontenttype("application/json")
            .type("de.telekom.ei.listener")
            .data(data)
            .build();

    Span newSpan = this.tracer.nextSpan().name(spanName).start();

    event.setSpanId(newSpan.context().spanId());

    newSpan.tag("spectre.issue", listener.getIssue());
    newSpan.tag("spectre.provider", listener.getServiceOwner());
    newSpan.tag("spectre.consumer", jc.getConsumer());
    // newSpan.tag("span.kind", "client");
    newSpan.end();

    return event;
  }

  private Mono<Void> publishEvent(Spectre event, JumperConfig jc) {

    // determine environment for local issuer and routing path on qa
    String envName = determineEnvironment(jc);

    return publishEventMono(
            publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName),
            oauthTokenUtil.generateGatewayTokenForPublisher(
                localIssuerUrl + "/" + envName, envName),
            event)
        .onErrorResume(
            throwable -> {
              log.error("Error publishing Spectre event", throwable);
              return Mono.empty(); // Don't fail the main request flow
            });
  }

  private String determineEnvironment(JumperConfig jc) {

    // should be always available
    if (jc.getGatewayClient().getIssuer() != null) {
      return jc.getGatewayClient().getIssuer().replaceFirst(".*realms/", "");
    }

    // as a fallback value we use realm already defined within jumper config
    return jc.getRealmName();
  }

  private Mono<Void> publishEventMono(String url, String token, Spectre event) {
    final Mono<Void> responseMono =
        spectreServiceWebClient
            .post()
            .uri(url)
            .headers(
                httpHeaders -> {
                  httpHeaders.setBearerAuth(token);

                  // pass tracing info from request to spectre, maybe also new client span should be
                  // created
                  Span currentSpan = tracer.currentSpan();
                  if (currentSpan != null) {
                    httpHeaders.set(
                        Constants.HEADER_X_B3_TRACE_ID, currentSpan.context().traceId());
                    httpHeaders.set(Constants.HEADER_X_B3_SPAN_ID, event.getSpanId());
                  }

                  // pass Spectre related info also as a header
                  httpHeaders.set(Constants.HEADER_X_SPECTRE_ISSUE, event.getData().getIssue());
                  httpHeaders.set(
                      Constants.HEADER_X_SPECTRE_PROVIDER, event.getData().getProvider());
                  httpHeaders.set(
                      Constants.HEADER_X_SPECTRE_CONSUMER, event.getData().getConsumer());
                })
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(event))
            .retrieve()
            .onStatus(
                HttpStatusCode::isError,
                response -> {
                  log.error("while publishing event got error status: {}", response.statusCode());
                  logDebugResponse(response);
                  return Mono.empty();
                })
            .onStatus(
                status -> !HttpStatus.CREATED.equals(status),
                response -> {
                  log.warn(
                      "while publishing event got unexpected status: {}", response.statusCode());
                  logDebugResponse(response);
                  return Mono.empty();
                })
            .bodyToMono(Void.class)
            .doOnSuccess(status -> log.debug("publishEventMono success"));

    return responseMono.then(Mono.defer(Mono::empty));
  }

  private static void logDebugResponse(ClientResponse response) {
    if (SpectreService.log.isDebugEnabled()) {
      SpectreService.log.debug("Response headers: {}", response.headers().asHttpHeaders());
      response
          .bodyToMono(String.class)
          .publishOn(Schedulers.boundedElastic())
          .subscribe(body -> SpectreService.log.debug("Response body: {}", body));
    }
  }

  private Object parsePayload(MediaType mediaType, String payload) {

    if (Objects.nonNull(payload)
        && mediaType != null
        && spectreConfiguration.jsonContentTypesContains(mediaType)) {

      log.debug("json compatible content-type, will try to parse as json payload");
      try {
        // try to return payload as json
        return ObjectMapperUtil.getInstance().readTree(payload);
      } catch (JsonProcessingException e) {
        log.error("error while parsing json payload for spectre", e);
      }
    }

    return payload;
  }
}

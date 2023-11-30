// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreData;
import jumper.model.config.SpectreKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.sleuth.CurrentTraceContext;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.web.WebFluxSleuthOperators;
import org.springframework.http.HttpStatus;
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
  private final CurrentTraceContext currentTraceContext;
  private final WebClient spectreServiceWebClient;

  @Value("${jumper.stargate.url}")
  private String stargateUrl;

  @Value("${jumper.issuer.url}")
  private String localIssuerUrl;

  @Value("${horizon.publishEventUrl}")
  private String publishEventUrl;

  public void handleEvent(
      JumperConfig jc,
      ServerWebExchange exchange,
      Object http,
      RouteListener listener,
      String payload) {
    WebFluxSleuthOperators.withSpanInScope(
        tracer,
        currentTraceContext,
        exchange,
        () -> publishEvent(createEvent(jc, exchange, http, listener, payload), jc));
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

    String finalSpanName = spanName;

    Span newSpan = this.tracer.nextSpan().name(finalSpanName).start();
    tracer.withSpan(newSpan);

    event.setSpanId(newSpan.context().spanId());

    newSpan.tag("spectre.issue", listener.getIssue());
    newSpan.tag("spectre.provider", listener.getServiceOwner());
    newSpan.tag("spectre.consumer", jc.getConsumer());
    // newSpan.tag("span.kind", "client");
    newSpan.end();

    return event;
  }

  private void publishEvent(Spectre event, JumperConfig jc) {

    String eventJson = null;
    try {
      eventJson = new ObjectMapper().writeValueAsString(event);
    } catch (JsonProcessingException e1) {
      e1.printStackTrace();
    }

    // determine environment for local issuer and routing path on qa
    String envName = determineEnvironment(jc);

    publishEventMono(
            publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName),
            eventJson,
            oauthTokenUtil.generateGatewayTokenForPublisher(
                localIssuerUrl + "/" + envName, envName),
            event.getSpanId())
        .subscribe();
  }

  private String determineEnvironment(JumperConfig jc) {

    // should be always available
    if (jc.getGatewayClient().getIssuer() != null) {
      return jc.getGatewayClient().getIssuer().replaceFirst(".*realms/", "");
    }

    // as a fallback value we use realm already defined within jumper config
    return jc.getRealmName();
  }

  private Mono<Void> publishEventMono(String url, String eventJson, String token, String spanId) {
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
                    httpHeaders.set(Constants.HEADER_X_B3_SPAN_ID, spanId);
                  }
                })
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(eventJson))
            .retrieve()
            .onStatus(
                HttpStatus::isError,
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
        && mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {

      log.debug("json compatible content-type, will try to parse as json payload");
      try {
        // try to return payload as json
        return new ObjectMapper().readTree(payload);
      } catch (JsonProcessingException e) {
        e.printStackTrace();
      }
    }

    return payload;
  }
}

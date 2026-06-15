// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import jumper.Constants;
import jumper.config.SpectreConfiguration;
import jumper.config.SpectreDirectPublishConfiguration;
import jumper.model.config.JumperConfig;
import jumper.model.config.RouteListener;
import jumper.model.config.Spectre;
import jumper.model.config.SpectreData;
import jumper.model.config.SpectreDirectPublishRule;
import jumper.model.config.SpectreKind;
import jumper.util.ObjectMapperUtil;
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

  /** Generic Spectre event type used for all listeners (Horizon-Galaxy multiplexes from here). */
  private static final String GENERIC_LISTENER_EVENT_TYPE = "de.telekom.ei.listener";

  /**
   * Counter incremented whenever an event is published directly to a team-specific event type.
   * TEMPORARY (World Cup 2026 peak-load mitigation) — used to validate the Galaxy load drop.
   */
  private static final String METRIC_SPECTRE_DIRECT_PUBLISH = "jumper.spectre.direct_publish";

  private final TokenGeneratorService tokenGeneratorService;
  private final Tracer tracer;
  private final SpectreDirectPublishConfiguration directPublishConfiguration;
  private final MeterRegistry meterRegistry;

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

    String eventType = resolveEventType(jc, listener);

    Spectre event =
        Spectre.builder()
            .specversion("1.0")
            .source(stargateUrl)
            .id(UUID.randomUUID())
            .datacontenttype("application/json")
            .type(eventType)
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

  /**
   * TEMPORARY (World Cup 2026 peak-load mitigation).
   *
   * <p>Determines the event type the Spectre event is published with. For consumer/route(/provider)
   * tuples configured in {@link SpectreDirectPublishConfiguration}, the event is scoped directly to
   * the team-specific target event type, bypassing the Horizon-Galaxy multiplex step. For all other
   * traffic the generic {@code de.telekom.ei.listener} type is used (default behaviour).
   *
   * <p>Remove together with the direct-publish mechanism once Horizon-Galaxy performance is fixed.
   */
  private String resolveEventType(JumperConfig jc, RouteListener listener) {
    return resolveTargetEventType(
            directPublishConfiguration.getRules(),
            jc.getConsumer(),
            jc.getApiBasePath(),
            listener.getServiceOwner())
        .map(targetEventType -> recordDirectPublish(jc, targetEventType))
        .orElse(GENERIC_LISTENER_EVENT_TYPE);
  }

  /**
   * Returns the target event type of the first {@link SpectreDirectPublishRule} matching the given
   * consumer / API base path / provider, or empty if none matches.
   *
   * <p>A rule matches when its {@code consumer} and {@code apiBasePath} equal the given values and
   * its {@code provider} is either unset (not constrained) or equal to the given provider.
   *
   * <p>Package-private and static to keep the matching logic unit-testable without the surrounding
   * Spring context.
   */
  static Optional<String> resolveTargetEventType(
      List<SpectreDirectPublishRule> rules, String consumer, String apiBasePath, String provider) {
    if (rules == null) {
      return Optional.empty();
    }

    return rules.stream()
        .filter(rule -> matchesRule(rule, consumer, apiBasePath, provider))
        .map(SpectreDirectPublishRule::getTargetEventType)
        .findFirst();
  }

  private static boolean matchesRule(
      SpectreDirectPublishRule rule, String consumer, String apiBasePath, String provider) {
    boolean consumerAndPathMatch =
        Objects.equals(rule.getConsumer(), consumer)
            && Objects.equals(rule.getApiBasePath(), apiBasePath);

    // provider is optional: only constrain the match when the rule defines one
    boolean providerMatches =
        rule.getProvider() == null || Objects.equals(rule.getProvider(), provider);

    return consumerAndPathMatch && providerMatches;
  }

  private String recordDirectPublish(JumperConfig jc, String targetEventType) {
    // debug only: event volume is high under peak load, info-level would spam the logs
    log.debug(
        "Spectre direct-publish (temporary WC2026 fix): consumer={}, apiBasePath={} -> type={}",
        jc.getConsumer(),
        jc.getApiBasePath(),
        targetEventType);

    List<Tag> tags =
        List.of(
            Tag.of("consumer", jc.getConsumer()),
            Tag.of("api_base_path", jc.getApiBasePath()),
            Tag.of("target_event_type", targetEventType));
    meterRegistry.counter(METRIC_SPECTRE_DIRECT_PUBLISH, tags).increment();

    return targetEventType;
  }

  private Mono<Void> publishEvent(Spectre event, JumperConfig jc) {

    // determine environment for local issuer and routing path on qa
    String envName = determineEnvironment(jc);

    return publishEventMono(
            publishEventUrl.replaceFirst(Constants.ENVIRONMENT_PLACEHOLDER, envName),
            tokenGeneratorService.generateGatewayTokenForPublisher(
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

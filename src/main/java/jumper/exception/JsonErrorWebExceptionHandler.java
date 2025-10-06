// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import jumper.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties.Resources;
import org.springframework.boot.autoconfigure.web.reactive.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
public class JsonErrorWebExceptionHandler extends DefaultErrorWebExceptionHandler {

  private final Tracer tracer;

  @Value("${spring.application.name}")
  private String applicationName;

  private Map<String, String> customResponseHeaders;

  public JsonErrorWebExceptionHandler(
      ErrorAttributes errorAttributes,
      Resources resources,
      ErrorProperties errorProperties,
      ApplicationContext applicationContext,
      Tracer tracer) {

    super(errorAttributes, resources, errorProperties, applicationContext);
    this.tracer = tracer;
  }

  @Override
  protected Map<String, Object> getErrorAttributes(
      ServerRequest request, ErrorAttributeOptions options) {

    // Here the logic can actually be customized according to the exception type
    Throwable error = super.getError(request);

    MergedAnnotation<ResponseStatus> responseStatusAnnotation =
        MergedAnnotations.from(error.getClass(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
            .get(ResponseStatus.class);

    HttpStatus errorStatus = findHttpStatus(request, error, responseStatusAnnotation);
    Map<String, Object> errorAttributes = new HashMap<>(8);

    errorAttributes.put("service", applicationName);
    errorAttributes.put("timestamp", new Date());
    errorAttributes.put("message", determineMessage(error, responseStatusAnnotation));
    errorAttributes.put("error", errorStatus.getReasonPhrase());
    errorAttributes.put("status", errorStatus.value());
    errorAttributes.put("method", request.method().name());

    errorAttributes.put(
        "traceId",
        (Objects.nonNull(request.headers().firstHeader(Constants.HEADER_X_B3_TRACE_ID)))
            ? request.headers().firstHeader(Constants.HEADER_X_B3_TRACE_ID)
            : "");

    errorAttributes.put(
        "tardisTraceId",
        (Objects.nonNull(request.headers().firstHeader(Constants.HEADER_X_TARDIS_TRACE_ID)))
            ? request.headers().firstHeader(Constants.HEADER_X_TARDIS_TRACE_ID)
            : "");

    writeErrorSpan(error, errorAttributes);

    // should also evaluate include options (stacktrace, message, bindingErrors)
    return errorAttributes;
  }

  @Override
  protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
    return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
  }

  @Override
  protected Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
    Map<String, Object> error =
        this.getErrorAttributes(request, this.getErrorAttributeOptions(request, MediaType.ALL));
    ServerResponse.BodyBuilder responseBuilder =
        ServerResponse.status(this.getHttpStatus(error)).contentType(MediaType.APPLICATION_JSON);

    if (!customResponseHeaders.isEmpty()) {
      customResponseHeaders.forEach(responseBuilder::header);
    }

    return responseBuilder.body(BodyInserters.fromValue(error));
  }

  @Override
  protected int getHttpStatus(Map<String, Object> errorAttributes) {
    int code =
        (int) errorAttributes.getOrDefault("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

    log.debug("errorAttributes {}", errorAttributes);
    // Here you can actually customize the HTTP response code based on the attributes inside the
    // errorAttributes

    return code;
  }

  private HttpStatus findHttpStatus(
      ServerRequest request,
      Throwable error,
      MergedAnnotation<ResponseStatus> responseStatusAnnotation) {
    customResponseHeaders = new HashMap<>();

    if (error instanceof ResponseStatusException) {
      return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    /*
    io.netty.channel.ConnectTimeoutException
    io.netty.channel.AbstractChannel$AnnotatedConnectException
     */

    if (error instanceof java.net.ConnectException
        || error instanceof SslHandshakeTimeoutException) {
      logError(request, error);
      return HttpStatus.GATEWAY_TIMEOUT;
    }

    if (error instanceof java.net.UnknownHostException) {
      logError(request, error);
      customResponseHeaders.put("Retry-After", "30");
      return HttpStatus.SERVICE_UNAVAILABLE;
    }

    return responseStatusAnnotation
        .getValue("code", HttpStatus.class)
        .orElse(INTERNAL_SERVER_ERROR);
  }

  private void logError(ServerRequest request, Throwable throwable) {
    log.error(request.exchange().getLogPrefix() + this.formatError(throwable, request));
  }

  private String formatError(Throwable ex, ServerRequest request) {
    String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
    return "Resolved [" + reason + "] for HTTP " + request.methodName() + " " + request.path();
  }

  private String determineMessage(
      Throwable error, MergedAnnotation<ResponseStatus> responseStatusAnnotation) {

    if (error instanceof ResponseStatusException) {
      return ((ResponseStatusException) error).getReason();

    } else {
      String reason = responseStatusAnnotation.getValue("reason", String.class).orElse("");
      if (StringUtils.hasText(reason)) {
        return reason;

      } else {
        return Objects.nonNull(error.getMessage()) ? error.getMessage() : "";
      }
    }
  }

  private void writeErrorSpan(Throwable error, Map<String, Object> errorAttributes) {
    Span errorSpan = this.tracer.nextSpan().name("error").start();

    errorSpan.tag("message", (String) errorAttributes.get("message"));
    errorSpan.tag("http.status_code", errorAttributes.get("status").toString());
    errorSpan.tag("http.method", (String) errorAttributes.get("method"));
    errorSpan.tag("x-tardis-traceid", (String) errorAttributes.get("tardisTraceId"));
    errorSpan.error(error);

    errorSpan.end();
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public class HeaderUtil {

  private HeaderUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static String getFirstValueFromHeaderField(ServerHttpRequest request, String headerName) {
    return request.getHeaders().getFirst(headerName);
  }

  public static String getLastValueFromHeaderField(ServerHttpRequest request, String headerName) {
    return request.getHeaders().getValuesAsList(headerName).stream()
        .reduce((first, last) -> last)
        .orElse(null);
  }

  public static void addHeader(ServerWebExchange exchange, String headerName, String headerValue) {
    exchange.getRequest().mutate().header(headerName, headerValue).build();
  }

  public static void removeHeader(ServerWebExchange exchange, String headerName) {
    exchange.getRequest().mutate().headers(httpHeaders -> httpHeaders.remove(headerName)).build();
  }

  public static void removeHeaders(ServerWebExchange exchange, List<String> headerList) {
    if (Objects.isNull(headerList) || headerList.isEmpty()) return;
    exchange
        .getRequest()
        .mutate()
        .headers(httpHeaders -> headerList.forEach(httpHeaders::remove))
        .build();
  }

  public static void rewriteXForwardedHeader(
      ServerWebExchange exchange, JumperConfig jumperConfig) {

    if (Objects.nonNull(jumperConfig.getConsumerOriginStargate())) {
      try {
        URL url = new URL(jumperConfig.getConsumerOriginStargate());
        HeaderUtil.addHeader(exchange, Constants.HEADER_X_FORWARDED_HOST, url.getHost());
      } catch (MalformedURLException e) {
        log.error(e.getMessage(), e);
      }
    }

    addHeader(exchange, Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT);
    addHeader(
        exchange, Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);
  }
}

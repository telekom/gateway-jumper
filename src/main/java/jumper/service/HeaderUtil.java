// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;

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

  public static void addHeader(
      ServerHttpRequest.Builder builder, String headerName, String headerValue) {
    builder.header(headerName, headerValue);
  }

  public static void removeHeader(ServerHttpRequest.Builder builder, String headerName) {
    builder.headers(httpHeaders -> httpHeaders.remove(headerName));
  }

  public static void removeHeaders(ServerHttpRequest.Builder builder, List<String> headerList) {
    if (Objects.isNull(headerList) || headerList.isEmpty()) return;
    builder.headers(httpHeaders -> headerList.forEach(httpHeaders::remove));
  }

  public static void rewriteXForwardedHeader(
      ServerHttpRequest.Builder builder, JumperConfig jumperConfig) {

    if (Objects.nonNull(jumperConfig.getConsumerOriginStargate())) {
      try {
        URI url = new URI(jumperConfig.getConsumerOriginStargate());
        HeaderUtil.addHeader(builder, Constants.HEADER_X_FORWARDED_HOST, url.getHost());
      } catch (URISyntaxException e) {
        log.error(e.getMessage(), e);
      }
    }

    addHeader(builder, Constants.HEADER_X_FORWARDED_PORT, Constants.HEADER_X_FORWARDED_PORT_PORT);
    addHeader(
        builder, Constants.HEADER_X_FORWARDED_PROTO, Constants.HEADER_X_FORWARDED_PROTO_HTTPS);
  }
}

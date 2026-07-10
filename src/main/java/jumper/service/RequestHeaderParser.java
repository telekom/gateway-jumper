// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Base64;
import java.util.List;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.request.HeaderConfig;
import jumper.util.HeaderUtil;
import jumper.util.ObjectMapperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/**
 * Decodes all request headers that can contribute to Jumper configuration.
 *
 * <p>The Base64-encoded JSON in {@code jumper_config} and {@code routing_config} is deserialized
 * into wire-only {@link JumperConfig} objects. Supported legacy and supplemental HTTP headers are
 * captured separately in an immutable {@link HeaderConfig} snapshot.
 *
 * <p>This class only parses input. {@link JumperConfigResolver} selects the applicable top-level or
 * routing configuration, while {@link EffectiveRequestConfigResolver} applies precedence between
 * the selected configuration and legacy headers.
 */
@Slf4j
@Component
public class RequestHeaderParser {

  public JumperConfig readJumperConfig(ServerHttpRequest request) {
    return fromJsonBase64(
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_JUMPER_CONFIG));
  }

  public List<JumperConfig> readRoutingConfigs(ServerHttpRequest request) {
    String routingConfigBase64 =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_ROUTING_CONFIG);

    if (StringUtils.isBlank(routingConfigBase64)) {
      throw new RuntimeException("can not base64decode header: " + routingConfigBase64);
    }

    return fromJsonBase64(routingConfigBase64, new TypeReference<>() {});
  }

  public HeaderConfig readHeaderConfig(ServerHttpRequest request) {
    return new HeaderConfig(
        request.getHeaders().containsHeader(Constants.HEADER_ROUTING_CONFIG),
        lastValue(request, Constants.HEADER_REMOTE_API_URL),
        lastValue(request, Constants.HEADER_ISSUER),
        lastValue(request, Constants.HEADER_CLIENT_ID),
        lastValue(request, Constants.HEADER_CLIENT_SECRET),
        lastValue(request, Constants.HEADER_API_BASE_PATH),
        lastValue(request, Constants.HEADER_REALM),
        lastValue(request, Constants.HEADER_ENVIRONMENT),
        lastValue(request, Constants.HEADER_TOKEN_ENDPOINT),
        lastValue(request, Constants.HEADER_CLIENT_SCOPES),
        lastValue(request, Constants.HEADER_X_SPACEGATE_CLIENT_ID),
        lastValue(request, Constants.HEADER_X_SPACEGATE_CLIENT_SECRET),
        lastValue(request, Constants.HEADER_X_SPACEGATE_SCOPE));
  }

  private static String lastValue(ServerHttpRequest request, String header) {
    return HeaderUtil.getLastValueFromHeaderField(request, header);
  }

  public static String toJsonBase64(Object value) {
    String jsonConfigBase64 = null;
    try {
      String decodedJson = ObjectMapperUtil.getInstance().writeValueAsString(value);
      jsonConfigBase64 = Base64.getEncoder().encodeToString(decodedJson.getBytes());
    } catch (JacksonException e) {
      log.error("can not base64encode object: " + value);
    }

    return jsonConfigBase64;
  }

  public static JumperConfig fromJsonBase64(String jsonConfigBase64) {
    if (StringUtils.isNotBlank(jsonConfigBase64)) {
      return fromJsonBase64(jsonConfigBase64, new TypeReference<>() {});
    }

    return new JumperConfig();
  }

  private static <T> T fromJsonBase64(String jsonConfigBase64, TypeReference<T> typeReference) {
    String decodedJson = new String(Base64.getDecoder().decode(jsonConfigBase64.getBytes()));
    try {
      return ObjectMapperUtil.getInstance().readValue(decodedJson, typeReference);
    } catch (JacksonException e) {
      throw new RuntimeException("can not base64decode header: " + jsonConfigBase64);
    }
  }
}

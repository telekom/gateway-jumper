// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.rewrite;

import java.util.Base64;
import java.util.Objects;
import jumper.config.SpectreConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

@Slf4j
public abstract class AbstractBodyRewrite {

  @Value("${spring.codec.max-in-memory-size}")
  private int limit;

  @Autowired private SpectreConfiguration spectreConfiguration;

  String getBodyForContentType(MediaType mediaType, byte[] originalBody) {
    String bodyToStore;

    if (isText(mediaType)) {
      bodyToStore = new String(originalBody);

    } else {
      log.debug("MediaType identified as non text, store as base64");
      bodyToStore = Base64.getEncoder().encodeToString(originalBody);
    }

    if (bodyToStore.length() > limit) {
      log.debug("payload string exceeded limit, will not be stored");
      bodyToStore = "";
    }

    log.debug("storing: {}", bodyToStore);
    return bodyToStore;
  }

  private boolean isText(MediaType mediaType) {
    return Objects.nonNull(mediaType)
        && (mediaType.isCompatibleWith(MediaType.parseMediaType("text/*"))
            || spectreConfiguration.jsonContentTypesContains(mediaType)
            || mediaType.isCompatibleWith(MediaType.APPLICATION_XML));
  }
}

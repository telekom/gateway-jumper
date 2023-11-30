// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.rewrite;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.Base64Utils;

@Slf4j
public abstract class AbstractBodyRewrite {

  @Value("${spring.codec.max-in-memory-size}")
  private int limit;

  String getBodyForContentType(MediaType mediaType, byte[] originalBody) {
    String bodyToStore;

    if (isText(mediaType)) {
      bodyToStore = new String(originalBody);

    } else {
      log.debug("MediaType identified as non text, store as base64");
      bodyToStore = Base64Utils.encodeToString(originalBody);
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
            || mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)
            || mediaType.isCompatibleWith(MediaType.APPLICATION_XML));
  }
}

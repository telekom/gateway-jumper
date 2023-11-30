// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.rewrite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.Spectre;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class SpectreBodyRewrite implements RewriteFunction<String, String> {

  @Override
  public Publisher<String> apply(ServerWebExchange exchange, String body) {
    MultiValueMap<String, String> params = exchange.getRequest().getQueryParams();

    String id = params.getFirst(Constants.QUERY_PARAM_LISTENER);

    log.debug("Spectre: payload={}", body);

    // adjust EventType.<applicationId>
    Spectre event = adjustEventType(body, id);

    String eventJson = null;
    try {
      eventJson = new ObjectMapper().writeValueAsString(event);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    log.debug("Spectre: adjusted={}", eventJson);

    return Mono.just(Objects.requireNonNull(eventJson));
  }

  private Spectre adjustEventType(String body, String id) {
    Spectre event = null;

    try {
      event = new ObjectMapper().readValue(body, Spectre.class);
      event.setType(event.getType() + "." + id);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return event;
  }
}

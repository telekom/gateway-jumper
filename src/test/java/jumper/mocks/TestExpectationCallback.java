// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static jumper.config.Config.*;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.Set;

/**
 * WireMock equivalent of the former MockServer ExpectationResponseCallback. Produces a dynamic
 * response: echoes request headers/body and derives the status code from the {@code statusCode}
 * query parameter, matching the original behaviour.
 */
public class TestExpectationCallback implements ResponseDefinitionTransformerV2 {

  public static final String NAME = "test-callback";

  // hop-by-hop / framework managed headers that must not be echoed back verbatim
  private static final Set<String> SKIP_HEADERS =
      Set.of("content-length", "transfer-encoding", "host", "connection");

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public ResponseDefinition transform(ServeEvent serveEvent) {
    Request request = serveEvent.getRequest();
    String path = stripQuery(request.getUrl());

    if (isFailoverPath(path)) {
      return echoHeaders(new ResponseDefinitionBuilder(), request).withStatus(200).build();
    } else if (path.endsWith("/callback")) {
      return echoHeaders(new ResponseDefinitionBuilder(), request)
          .withStatus(statusCode(request))
          .withBody(request.getBodyAsString())
          .build();
    } else if (path.endsWith("/v1/events")
        && List.of("HEAD", "POST").contains(request.getMethod().value())) {
      return new ResponseDefinitionBuilder().withStatus(statusCode(request)).build();
    } else {
      return new ResponseDefinitionBuilder().withStatus(404).build();
    }
  }

  private int statusCode(Request request) {
    return Integer.parseInt(request.queryParameter("statusCode").firstValue());
  }

  private ResponseDefinitionBuilder echoHeaders(
      ResponseDefinitionBuilder builder, Request request) {
    for (HttpHeader header : request.getHeaders().all()) {
      if (!SKIP_HEADERS.contains(header.key().toLowerCase())) {
        builder.withHeader(header.key(), header.values().toArray(new String[0]));
      }
    }
    return builder;
  }

  private String stripQuery(String url) {
    int idx = url.indexOf('?');
    return idx >= 0 ? url.substring(0, idx) : url;
  }

  private boolean isFailoverPath(String path) {
    return List.of(REMOTE_BASE_PATH, REMOTE_FAILOVER_BASE_PATH, REMOTE_PROVIDER_BASE_PATH)
        .contains(path);
  }
}

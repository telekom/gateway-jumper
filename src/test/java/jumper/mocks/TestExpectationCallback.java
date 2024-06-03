// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import static jumper.config.Config.*;
import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class TestExpectationCallback implements ExpectationResponseCallback {
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    if (isFailoverPath(httpRequest.getPath().getValue())) {
      return response().withHeaders(httpRequest.getHeaders()).withStatusCode(200);
    } else if (httpRequest.getPath().getValue().endsWith("/callback")) {
      return response()
          .withHeaders(httpRequest.getHeaders())
          .withBody(httpRequest.getBodyAsString())
          .withStatusCode(Integer.parseInt(httpRequest.getFirstQueryStringParameter("statusCode")));
    } else if (httpRequest.getPath().getValue().endsWith("/v1/events")
        && List.of("HEAD", "POST").contains(httpRequest.getMethod())) {
      return response()
          .withStatusCode(Integer.parseInt(httpRequest.getFirstQueryStringParameter("statusCode")));
    } else {
      return notFoundResponse();
    }
  }

  private boolean isFailoverPath(String path) {
    return List.of(REMOTE_BASE_PATH, REMOTE_FAILOVER_BASE_PATH, REMOTE_PROVIDER_BASE_PATH)
        .contains(path);
  }
}

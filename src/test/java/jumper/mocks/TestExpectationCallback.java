package jumper.mocks;

import static org.mockserver.model.HttpResponse.notFoundResponse;
import static org.mockserver.model.HttpResponse.response;

import java.util.List;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

public class TestExpectationCallback implements ExpectationResponseCallback {
  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    if (httpRequest.getPath().getValue().endsWith("/callback")) {
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
}

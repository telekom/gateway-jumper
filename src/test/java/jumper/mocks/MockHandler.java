// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.micrometer.tracing.Tracer;
import jumper.BaseSteps;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

@RequiredArgsConstructor
public class MockHandler {

  private final BaseSteps baseSteps;

  MockUpstreamServer mockUpstreamServer;
  MockIrisServer mockIrisServer;

  static MockHorizonServer mockHorizonServer = new MockHorizonServer();

  @Autowired Tracer tracer;

  @Autowired WebTestClient webTestClient;

  @Before("@upstream")
  public void beforeUpstream() {
    mockUpstreamServer = new MockUpstreamServer();
    mockUpstreamServer.startServer();
    this.baseSteps.setMockUpstreamServer(mockUpstreamServer);

    this.baseSteps.setWebTestClient(webTestClient);

    var span = tracer.nextSpan();
    this.baseSteps.setId(span.context().traceId());
    this.baseSteps.setSpanId(span.context().spanId());
  }

  @After("@upstream")
  public void afterUpstream() {
    mockUpstreamServer.stopServer();
  }

  @Before("@iris")
  public void beforeIris() {
    mockIrisServer = new MockIrisServer();
    mockIrisServer.startServer();
    this.baseSteps.setMockIrisServer(mockIrisServer);
  }

  @After("@iris")
  public void afterIris() {
    mockIrisServer.stopServer();
    int defaultIrisResponse = 200;
    mockIrisServer.setResponse(defaultIrisResponse);
  }

  @Before("@horizon")
  public void beforeEach() {
    this.baseSteps.setMockHorizonServer(mockHorizonServer);
  }

  @After("@horizon")
  public void afterEach() {
    mockHorizonServer.resetMockServer();
  }
}

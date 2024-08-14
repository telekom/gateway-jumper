// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import java.util.Random;
import jumper.BaseSteps;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

@RequiredArgsConstructor
public class MockHandler {

  private final BaseSteps baseSteps;

  MockUpstreamServer mockUpstreamServer;
  MockIrisServer mockIrisServer;
  MockHorizonServer mockHorizonServer;

  @Autowired WebTestClient webTestClient;

  @Before("@upstream")
  public void beforeUpstream() {
    mockUpstreamServer = new MockUpstreamServer();
    mockUpstreamServer.startServer();
    this.baseSteps.setMockUpstreamServer(mockUpstreamServer);

    this.baseSteps.setWebTestClient(webTestClient);

    Random random = new Random();
    this.baseSteps.setId(String.format("%016x", random.nextLong()));
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
  public void beforeHorizon() {
    mockHorizonServer = new MockHorizonServer(baseSteps);
    mockHorizonServer.startServer();
    this.baseSteps.setMockHorizonServer(mockHorizonServer);
  }

  @After("@horizon")
  public void afterHorizon() {
    mockHorizonServer.stopServer();
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.request;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JumperInfoRequest {

  private boolean meshActivated;
  private boolean lastMileSecurity;
  private boolean lastMileSecurityEnhanced;
  private boolean externalAuthorization;
  private boolean basicAuth;
  private boolean xTokenExchangeAuthorization;

  private IncomingRequest incomingRequest;

  public void setInfoScenario(
      boolean lastMileSecurity,
      boolean lastMileSecurityEnhanced,
      boolean meshActivated,
      boolean externalAuthorization,
      boolean basicAuth,
      boolean xTokenExchangeauthorization) {
    this.meshActivated = meshActivated;
    this.lastMileSecurity = lastMileSecurity;
    this.lastMileSecurityEnhanced = lastMileSecurityEnhanced;
    this.externalAuthorization = externalAuthorization;
    this.basicAuth = basicAuth;
    this.xTokenExchangeAuthorization = xTokenExchangeauthorization;
  }

  @Override
  public String toString() {

    String lineSeparator = System.getProperty("line.separator");
    StringBuilder sb = new StringBuilder();

    sb.append("Scenario:");
    sb.append(lineSeparator);
    sb.append("MeshActivated: ");
    sb.append(meshActivated);
    sb.append(" LastMileSecurity: ");
    sb.append(lastMileSecurity);
    sb.append(" LastMileSecurityEnhanced: ");
    sb.append(lastMileSecurityEnhanced);
    sb.append(" ExternalAuthorization: ");
    sb.append(externalAuthorization);
    sb.append(" XtokenExchangeAuthorization: ");
    sb.append(xTokenExchangeAuthorization);
    sb.append(" BasicAuth: ");
    sb.append(basicAuth);
    sb.append(lineSeparator);

    sb.append("IncomingRequest");
    sb.append(lineSeparator);

    sb.append("HTTP-Method: ").append(incomingRequest.getMethod());
    sb.append(lineSeparator);

    sb.append("Consumer: ").append(incomingRequest.getConsumer());
    sb.append(lineSeparator);

    sb.append("BasePath: ").append(incomingRequest.getBasePath());
    sb.append(lineSeparator);

    sb.append("Resource: ").append(incomingRequest.getRequestPath());
    sb.append(lineSeparator);

    sb.append("Host: ").append(incomingRequest.getFinalApiUrl());
    sb.append(lineSeparator);

    if (Objects.nonNull(incomingRequest.getLogEntries())) {
      Set<Entry<String, String>> entrySet = incomingRequest.getLogEntries().entrySet();
      for (Entry<String, String> entry : entrySet) {
        sb.append(entry.getKey()).append(": ").append(entry.getValue());
        sb.append(lineSeparator);
      }
    }

    return sb.toString();
  }
}

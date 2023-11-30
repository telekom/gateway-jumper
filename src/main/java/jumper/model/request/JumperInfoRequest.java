// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.request;

import java.util.Map.Entry;
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

  private String environment;

  private IncomingRequest incomingRequest;
  private OutgoingRequest outgoingRequest;

  public void setInfoScenario(
      boolean lastMileSecurity,
      boolean lastMileSecurityEnhanced,
      boolean meshActivated,
      boolean externalAuthorization,
      boolean basicAuth) {
    this.meshActivated = meshActivated;
    this.lastMileSecurity = lastMileSecurity;
    this.lastMileSecurityEnhanced = lastMileSecurityEnhanced;
    this.externalAuthorization = externalAuthorization;
    this.basicAuth = basicAuth;
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
    sb.append(" BasicAuth: ");
    sb.append(basicAuth);
    sb.append(lineSeparator);

    sb.append("IncomingRequest");
    sb.append(lineSeparator);

    sb.append("HTTP-Method: ").append(incomingRequest.getMethod());
    sb.append(lineSeparator);

    sb.append("Host: ").append(incomingRequest.getHost());
    sb.append(lineSeparator);

    sb.append("BasePath: ").append(incomingRequest.getBasePath());
    sb.append(lineSeparator);

    sb.append("Resource: ").append(incomingRequest.getResource());
    sb.append(lineSeparator);

    Set<Entry<String, String>> entrySet = incomingRequest.getLogEntries().entrySet();
    for (Entry<String, String> entry : entrySet) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue());
      sb.append(lineSeparator);
    }

    return sb.toString();
  }
}

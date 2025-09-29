// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import jumper.Constants;
import jumper.model.config.JumperConfig;
import jumper.model.request.JumperInfoRequest;
import lombok.Data;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

/**
 * Context object that encapsulates all data needed for request processing in the RequestFilter.
 * This helps reduce parameter passing and provides a clean interface for authentication strategies.
 */
@Data
public class RequestProcessingContext {

  private final ServerWebExchange exchange;
  private final RequestFilter.Config config;
  private final ServerHttpRequest readOnlyRequest;
  private final ServerHttpRequest.Builder requestBuilder;
  private final JumperConfig jumperConfig;
  private final Optional<JumperInfoRequest> jumperInfoRequest;
  private final URI finalApiUri;
  private final String currentZone;
  private final List<String> internetFacingZones;
  private final String localIssuerUrl;

  private ServerWebExchange finalExchange;

  /** Checks if the request is targeting localhost issuer service. */
  public boolean isRemoteHostTarget() {
    return !jumperConfig.getRemoteApiUrl().startsWith(Constants.LOCALHOST_ISSUER_SERVICE);
  }

  /** Checks if the current zone is internet-facing. */
  public boolean isCurrentZoneInternetFacing() {
    return currentZone != null && internetFacingZones.contains(currentZone);
  }

  /** Checks if the given zone is internet-facing. */
  public boolean isInternetFacingZone(String zone) {
    return zone != null && internetFacingZones.contains(zone);
  }
}

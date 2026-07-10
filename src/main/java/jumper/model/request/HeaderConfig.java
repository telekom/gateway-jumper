// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.request;

/**
 * Legacy and supplemental request configuration parsed from ordinary HTTP headers.
 *
 * <p>The values remain separate from the selected {@code JumperConfig} so precedence is explicit at
 * resolution time. {@code hasRoutingConfigHeader} identifies selected-routing requests, for which
 * target and authentication headers generally must not override or supplement the selected routing
 * entry.
 */
public record HeaderConfig(
    boolean hasRoutingConfigHeader,
    String remoteApiUrl,
    String issuer,
    String clientId,
    String clientSecret,
    String apiBasePath,
    String realm,
    String environment,
    String tokenEndpoint,
    String scopes,
    String xSpacegateClientId,
    String xSpacegateClientSecret,
    String xSpacegateScope) {}

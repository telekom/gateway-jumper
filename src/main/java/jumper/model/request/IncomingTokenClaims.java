// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.request;

import java.util.Date;
import java.util.Set;

/**
 * Typed subset of incoming consumer-token claims required by request processing.
 *
 * <p>Keeping this contract separate from configuration and raw JWT objects makes claim dependencies
 * explicit and prevents downstream filters from reparsing the token.
 */
public record IncomingTokenClaims(
    String clientId,
    String subject,
    String issuer,
    String originGateway,
    String originZone,
    Set<String> audiences,
    Date issuedAt,
    Date expiration) {}

// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import jumper.Constants;
import jumper.model.request.IncomingTokenClaims;
import jumper.util.HeaderUtil;
import jumper.util.OauthTokenUtil;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/** Parses the incoming bearer token once into the typed claims required by downstream filters. */
@Component
public class IncomingTokenClaimsParser {

  private static final String CLAIM_CLIENT_ID = "clientId";
  private static final String CLAIM_ORIGIN_GATEWAY = "originStargate";
  private static final String CLAIM_ORIGIN_ZONE = "originZone";

  public IncomingTokenClaims parse(ServerHttpRequest request) {
    String authorization =
        HeaderUtil.getLastValueFromHeaderField(request, Constants.HEADER_AUTHORIZATION);
    Jwt<?, Claims> token = OauthTokenUtil.getAllClaimsFromToken(authorization);
    Claims claims = token.getPayload();

    return new IncomingTokenClaims(
        claims.get(CLAIM_CLIENT_ID, String.class),
        claims.getSubject(),
        claims.getIssuer(),
        claims.get(CLAIM_ORIGIN_GATEWAY, String.class),
        claims.get(CLAIM_ORIGIN_ZONE, String.class),
        claims.getAudience(),
        claims.getIssuedAt(),
        claims.getExpiration());
  }
}

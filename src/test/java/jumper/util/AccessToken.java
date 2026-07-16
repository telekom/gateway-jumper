// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.config.Config.LOCAL_ISSUER;
import static jumper.config.Config.REMOTE_ISSUER;

import io.jsonwebtoken.Jwts;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Singular;

@Builder
public class AccessToken {

  private String clientId;

  /**
   * Value of the {@code azp} claim on the consumer token. Defaults to {@code clientId} (matching
   * real Iris tokens); pass a blank value to omit the claim entirely.
   */
  private String azp;

  private String env;
  private String originZone;
  private String originStargate;

  /**
   * Token audiences. Use {@code .audience("x")} to add a single value or {@code
   * .audiences(List.of(...))} for several — a multi-valued {@code aud} is emitted as a JSON array.
   */
  @Singular("audience")
  private List<String> audiences;

  public String getConsumerAccessToken() {
    HashMap<String, String> claims = new HashMap<>();
    claims.put("typ", "Bearer");
    String azpClaim = azp != null ? azp : clientId;
    if (azpClaim != null && !azpClaim.isBlank()) {
      claims.put("azp", azpClaim);
    }
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("originZone", originZone);
    claims.put("originStargate", originStargate);
    claims.put("clientId", clientId);

    return buildAccessToken(claims, audiences, LOCAL_ISSUER);
  }

  public String getIdpToken() {
    HashMap<String, String> claims = new HashMap<>();
    claims.put("typ", "Bearer");
    claims.put("azp", clientId);
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("clientId", clientId);
    claims.put("env", env);
    claims.put("originZone", originZone);
    claims.put("originStargate", originStargate);

    return buildAccessToken(claims, List.of(), REMOTE_ISSUER);
  }

  private String buildAccessToken(Map<String, String> claims, List<String> aud, String issuer) {

    Date issuedAt = new Date(System.currentTimeMillis());
    Date expiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));

    PrivateKey privateKey = null;
    try {
      privateKey = RsaUtils.getPrivateKey(Path.of("src/test/resources/keypair", "tls.key"));
    } catch (Exception e) {
      e.getStackTrace();
    }

    String keyId = "123456";

    assert privateKey != null;
    var builder =
        Jwts.builder().claims(claims).issuer(issuer).expiration(expiration).issuedAt(issuedAt);

    if (aud != null && !aud.isEmpty()) {
      builder.audience().add(aud).and();
    }

    return builder
        .signWith(privateKey, Jwts.SIG.RS256)
        .header()
        .keyId(keyId)
        .add("typ", "JWT")
        .and()
        .compact();
  }
}

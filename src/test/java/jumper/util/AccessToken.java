package jumper.util;

import static jumper.config.Config.LOCAL_ISSUER;
import static jumper.config.Config.REMOTE_ISSUER;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import jumper.Constants;
import jumper.model.config.KeyInfo;
import jumper.service.OauthTokenUtil;
import lombok.Builder;

@Builder
public class AccessToken {

  private String clientId;
  private String env;
  private String originZone;
  private String originStargate;
  private String audience;

  public String getConsumerAccessToken() {
    HashMap<String, String> claims = new HashMap<>();
    claims.put("typ", "Bearer");
    claims.put("azp", clientId);
    claims.put("sub", UUID.randomUUID().toString());
    claims.put("originZone", originZone);
    claims.put("originStargate", originStargate);
    claims.put("clientId", clientId);
    if (audience != null) claims.put("aud", audience);

    return buildAccessToken(claims, LOCAL_ISSUER);
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

    return buildAccessToken(claims, REMOTE_ISSUER);
  }

  private String buildAccessToken(Map<String, String> claims, String issuer) {

    Date issuedAt = new Date(System.currentTimeMillis());
    Date expiration = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
    KeyInfo keyInfo = null;
    try {
      keyInfo = OauthTokenUtil.loadKeyInfo().get(Constants.DEFAULT_REALM);
    } catch (IOException e) {
      e.getStackTrace();
    }

    String keyId = "123456";

    assert keyInfo != null;
    return Jwts.builder()
        .setClaims(claims)
        .setIssuer(issuer)
        .setExpiration(expiration)
        .setIssuedAt(issuedAt)
        .signWith(keyInfo.getPk(), SignatureAlgorithm.RS256)
        .setHeaderParam("kid", keyId)
        .setHeaderParam("typ", "JWT")
        .compact();
  }
}

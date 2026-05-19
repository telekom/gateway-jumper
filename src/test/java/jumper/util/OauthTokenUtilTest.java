// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static jumper.util.OauthTokenUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OauthTokenUtilTest {

  @Nested
  class getTokenWithoutSignatureTests {

    @Test
    @DisplayName("Should return token without signature from a valid JWT token")
    public void testGetTokenWithoutSignature_validToken_returnsTokenWithoutSignature() {
      String token = getTestToken(true);

      String returnedTokenWithoutSignature = getTokenWithoutSignature(token);
      token = token.trim();
      token = token.substring(token.indexOf(" ") + 1);

      String tokenWithoutSignature = token.substring(0, token.lastIndexOf(".") + 1);

      assertThat(returnedTokenWithoutSignature).isEqualTo(tokenWithoutSignature);
    }

    @Test
    @DisplayName("Should throw exception when getting no token")
    public void testGetTokenWithoutSignature_noToken_throwsException() {
      assertThrows(IllegalArgumentException.class, () -> getTokenWithoutSignature(null));
    }

    @Test
    @DisplayName("Should throw exception when getting token without bearer prefix from a JWT token")
    public void testGetTokenWithoutSignature_invalidToken_throwsException() {
      final String token = getTestToken(false);

      assertThrows(IllegalArgumentException.class, () -> getTokenWithoutSignature(token));
    }

    @Test
    @DisplayName("Should throw exception when getting token without a signature from a JWT token")
    public void testGetTokenWithoutSignature_invalidTokenNoSignature_throwsException() {
      String initialisedToken = getTestToken(true);

      initialisedToken = initialisedToken.trim();
      final String token = initialisedToken.substring(0, initialisedToken.lastIndexOf("."));

      assertThrows(IllegalArgumentException.class, () -> getTokenWithoutSignature(token));
    }
  }

  @Nested
  class getSignatureTests {

    @Test
    @DisplayName("Should return token without signature from a valid JWT token")
    public void testGetSignature_validToken_returnsSignature() {
      String token = getTestToken(true);

      String returnedSignature = getSignature(token);
      token = token.trim();
      token = token.substring(token.indexOf(" ") + 1);

      String tokenWithoutSignature = token.substring(token.lastIndexOf(".") + 1);

      assertThat(returnedSignature).isEqualTo(tokenWithoutSignature);
    }

    @Test
    @DisplayName("Should throw exception when getting no token")
    public void testGetSignature_noToken_throwsException() {
      assertThrows(IllegalArgumentException.class, () -> getSignature(null));
    }

    @Test
    @DisplayName("Should throw exception when getting token without bearer prefix from a JWT token")
    public void testGetSignature_invalidToken_throwsException() {
      final String token = getTestToken(false);

      assertThrows(IllegalArgumentException.class, () -> getSignature(token));
    }

    @Test
    @DisplayName("Should throw exception when getting token without a signature from a JWT token")
    public void testGetSignature_invalidTokenNoSignature_throwsException() {
      String initialisedToken = getTestToken(true);

      initialisedToken = initialisedToken.trim();
      final String token = initialisedToken.substring(0, initialisedToken.lastIndexOf("."));

      assertThrows(IllegalArgumentException.class, () -> getSignature(token));
    }
  }

  @Nested
  class getClaimFromTokenTests {

    @Test
    @DisplayName("valid token should return claims")
    public void testGetClaimFromToken_validToken_returnsClaims() {
      String token = getTestToken(true);

      String nameClaim = getClaimFromToken(token, "name");
      String scopeClaim = getClaimFromToken(token, "scope");

      assertThat(nameClaim).isEqualTo("test user");
      assertThat(scopeClaim).isEqualTo("dev");
    }

    @Test
    @DisplayName("Should throw exception when getting claim from no token")
    public void testGetClaimFromToken_noToken_throwsException() {
      assertThrows(IllegalArgumentException.class, () -> getClaimFromToken(null, "name"));
    }
  }

  @Nested
  class getAllClaimsFromTokenTests {

    @Test
    @DisplayName("Should return all claims from a valid JWT token")
    public void testGetAllClaimsFromToken_validToken_returnsAllClaims() {
      String token = getTestToken(false);

      token = token.substring(0, token.lastIndexOf(".") + 1);
      String header = getAllClaimsFromToken(token).getHeader().toString();
      String nameClaim = getAllClaimsFromToken(token).getBody().get("name", String.class);
      String scopeClaim = getAllClaimsFromToken(token).getBody().get("scope", String.class);

      assertThat(header).contains("alg=HS256");
      assertThat(nameClaim).isEqualTo("test user");
      assertThat(scopeClaim).isEqualTo("dev");
    }

    @Test
    @DisplayName("Should throw exception when getting claims from no token")
    public void testGetAllClaimsFromToken_noToken_throwsException() {
      assertThrows(UnsupportedJwtException.class, () -> getAllClaimsFromToken(null));
    }
  }

  @Nested
  class testWithMultipleBlanks {
    @Test
    @DisplayName("token with multiple blanks between bearer and token should return claims")
    public void testGetTokenWithoutSignature_tokenWithMoreThanOneSpace_throwsException() {
      String token = getTestTokenWithMultipleBlanks();

      String nameClaim = getClaimFromToken(token, "name");
      String scopeClaim = getClaimFromToken(token, "scope");

      assertThat(nameClaim).isEqualTo("test user");
      assertThat(scopeClaim).isEqualTo("dev");
    }
  }

  private String getTestToken(boolean withBearerPrefix) {

    if (withBearerPrefix) {
      return "Bearer " + createToken();
    } else {
      return createToken();
    }
  }

  private String getTestTokenWithMultipleBlanks() {
    return "Bearer      " + createToken();
  }

  private String createToken() {
    return Jwts.builder()
        .setIssuer("Narvi")
        .setSubject("tuser")
        .claim("name", "test user")
        .claim("scope", "dev")
        .setIssuedAt(Date.from(Instant.ofEpochSecond(1466796822L)))
        .setExpiration(Date.from(Instant.ofEpochSecond(4622470422L)))
        .signWith(
            Keys.hmacShaKeyFor(
                Base64.getDecoder().decode("Yn2kjibddFAWtnPJ2AFlL8WXmohJMCvigQggaEypa5E=")))
        .compact();
  }
}

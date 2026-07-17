// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import jumper.model.config.Claim;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ClaimUtil}, the resolver for configured aud claims (DHEI-21196). */
class ClaimUtilTest {

  private static final String CONSUMER_CLIENT_ID = "eni--local-team--local-app";

  @Test
  @DisplayName("a literal aud value is resolved as-is")
  void literalValue_isResolved() {
    List<Claim> claims = List.of(claim("aud", "hello-world", null));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly("hello-world");
  }

  @Test
  @DisplayName("valueFrom ConsumerClientId resolves to the consumer's client id")
  void consumerClientIdReference_isResolved() {
    List<Claim> claims = List.of(claim("aud", null, "ConsumerClientId"));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly(CONSUMER_CLIENT_ID);
  }

  @Test
  @DisplayName("the ConsumerClientId reference is matched case-insensitively")
  void consumerClientIdReference_matchesCaseInsensitively() {
    List<Claim> claims = List.of(claim("aud", null, "consumerclientid"));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly(CONSUMER_CLIENT_ID);
  }

  @Test
  @DisplayName("multiple aud entries resolve to an ordered list")
  void multipleAudEntries_resolveInOrder() {
    List<Claim> claims =
        List.of(
            claim("aud", "first-audience", null),
            claim("aud", null, "ConsumerClientId"),
            claim("aud", "third-audience", null));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly("first-audience", CONSUMER_CLIENT_ID, "third-audience");
  }

  @Test
  @DisplayName("entries with a non-aud key are ignored")
  void nonAudKey_isIgnored() {
    List<Claim> claims =
        List.of(claim("azp", "forged-azp", null), claim("sub", "forged-sub", null));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("an unknown valueFrom reference is skipped, not stamped")
  void unknownValueFrom_isSkipped() {
    List<Claim> claims =
        List.of(claim("aud", null, "ProviderClientId"), claim("aud", "literal-audience", null));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly("literal-audience");
  }

  @Test
  @DisplayName("null or empty claim lists resolve to an empty list")
  void nullOrEmptyClaims_resolveToEmpty() {
    assertThat(ClaimUtil.resolveAudiences(null, CONSUMER_CLIENT_ID)).isEmpty();
    assertThat(ClaimUtil.resolveAudiences(List.of(), CONSUMER_CLIENT_ID)).isEmpty();
  }

  @Test
  @DisplayName("a null claim entry is skipped instead of throwing")
  void nullClaimEntry_isSkipped() {
    // a malformed jumper_config like {"claims":{"default":[null]}} deserializes to a list
    // with a null element - it must degrade to the fallback aud, not crash token generation
    List<Claim> claims = Arrays.asList(null, claim("aud", "literal-audience", null));

    assertThat(ClaimUtil.resolveAudiences(claims, CONSUMER_CLIENT_ID))
        .containsExactly("literal-audience");
  }

  @Test
  @DisplayName("blank resolved values are dropped")
  void blankResolvedValues_areDropped() {
    // blank literal, and a ConsumerClientId reference with no resolvable client id
    List<Claim> claims = List.of(claim("aud", " ", null), claim("aud", null, "ConsumerClientId"));

    assertThat(ClaimUtil.resolveAudiences(claims, null)).isEmpty();
  }

  private static Claim claim(String key, String value, String valueFrom) {
    Claim claim = new Claim();
    claim.setKey(key);
    claim.setValue(value);
    claim.setValueFrom(valueFrom);
    return claim;
  }
}

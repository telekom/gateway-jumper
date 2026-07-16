// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jumper.Constants;
import jumper.model.config.Claim;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/** Resolves configured token claims from the jumper_config {@code claims} blob. */
@Slf4j
public final class ClaimUtil {

  private ClaimUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Resolves the configured {@code aud} values in order; blanks and non-aud keys are dropped.
   *
   * <p>Only {@code aud} entries are honoured: generic claim stamping on a self-signed token would
   * let jumper_config overwrite security-critical claims such as {@code azp} or {@code sub}. Only
   * {@code ConsumerClientId} is resolved at request time — other references reach Jumper
   * pre-resolved as literal values.
   */
  public static List<String> resolveAudiences(List<Claim> claims, String consumerClientId) {
    if (Objects.isNull(claims) || claims.isEmpty()) {
      return List.of();
    }
    List<String> resolvedAudiences = new ArrayList<>();
    for (Claim claim : claims) {
      if (!Constants.TOKEN_CLAIM_AUD.equals(claim.getKey())) {
        continue;
      }
      String resolved = resolve(claim, consumerClientId);
      if (StringUtils.isNotBlank(resolved)) {
        resolvedAudiences.add(resolved);
      }
    }
    return resolvedAudiences;
  }

  private static String resolve(Claim claim, String consumerClientId) {
    if (StringUtils.isNotBlank(claim.getValueFrom())) {
      if (Constants.CLAIM_VALUE_FROM_CONSUMER_CLIENT_ID.equalsIgnoreCase(claim.getValueFrom())) {
        return consumerClientId;
      }
      log.warn(
          "Unknown claim valueFrom '{}' for key '{}', skipping",
          claim.getValueFrom(),
          claim.getKey());
      return null;
    }
    return claim.getValue();
  }
}

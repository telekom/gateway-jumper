// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import jumper.model.config.SpectreDirectPublishRule;
import org.junit.jupiter.api.Test;

class SpectreServiceDirectPublishTest {

  private static final String TARGET_EVENT_TYPE =
      "de.telekom.ei.listener.sh-fsf--codebuster--pacman-controlit";

  private static SpectreDirectPublishRule rule(
      String apiBasePath, String consumer, String provider, String targetEventType) {
    SpectreDirectPublishRule rule = new SpectreDirectPublishRule();
    rule.setApiBasePath(apiBasePath);
    rule.setConsumer(consumer);
    rule.setProvider(provider);
    rule.setTargetEventType(targetEventType);
    return rule;
  }

  @Test
  void matchesOnConsumerAndApiBasePathWhenProviderNotConstrained() {
    // GIVEN: a rule without a provider constraint
    List<SpectreDirectPublishRule> rules =
        List.of(rule("/a4m/content/ott-service/v2", "coca--r2d2--amk", null, TARGET_EVENT_TYPE));

    // WHEN: resolving with a matching consumer + apiBasePath (provider irrelevant)
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/ott-service/v2", "any");

    // THEN: the target event type is returned
    assertThat(result).contains(TARGET_EVENT_TYPE);
  }

  @Test
  void matchesWhenProviderConstraintIsSatisfied() {
    // GIVEN: a rule constrained to a specific provider
    List<SpectreDirectPublishRule> rules =
        List.of(
            rule(
                "/a4m/content/ott-service/v2",
                "coca--r2d2--amk",
                "a4m--pluto--sputnik-odin",
                TARGET_EVENT_TYPE));

    // WHEN: resolving with the matching provider
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/ott-service/v2", "a4m--pluto--sputnik-odin");

    // THEN: the target event type is returned
    assertThat(result).contains(TARGET_EVENT_TYPE);
  }

  @Test
  void doesNotMatchWhenProviderConstraintIsViolated() {
    // GIVEN: a rule constrained to a specific provider
    List<SpectreDirectPublishRule> rules =
        List.of(
            rule(
                "/a4m/content/ott-service/v2",
                "coca--r2d2--amk",
                "a4m--pluto--sputnik-odin",
                TARGET_EVENT_TYPE));

    // WHEN: resolving with a different provider
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/ott-service/v2", "some--other--provider");

    // THEN: no match
    assertThat(result).isEmpty();
  }

  @Test
  void doesNotMatchOnDifferentConsumer() {
    // GIVEN
    List<SpectreDirectPublishRule> rules =
        List.of(rule("/a4m/content/ott-service/v2", "coca--r2d2--amk", null, TARGET_EVENT_TYPE));

    // WHEN: consumer differs
    var result =
        SpectreService.resolveTargetEventType(
            rules, "other--consumer", "/a4m/content/ott-service/v2", null);

    // THEN
    assertThat(result).isEmpty();
  }

  @Test
  void doesNotMatchOnDifferentApiBasePath() {
    // GIVEN
    List<SpectreDirectPublishRule> rules =
        List.of(rule("/a4m/content/ott-service/v2", "coca--r2d2--amk", null, TARGET_EVENT_TYPE));

    // WHEN: apiBasePath differs
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/other/v1", null);

    // THEN
    assertThat(result).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoRulesConfigured() {
    // GIVEN: empty rule list
    List<SpectreDirectPublishRule> rules = List.of();

    // WHEN
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/ott-service/v2", null);

    // THEN
    assertThat(result).isEmpty();
  }

  @Test
  void returnsFirstMatchingRule() {
    // GIVEN: two rules that both match, with different target event types
    List<SpectreDirectPublishRule> rules =
        List.of(
            rule("/a4m/content/ott-service/v2", "coca--r2d2--amk", null, TARGET_EVENT_TYPE),
            rule(
                "/a4m/content/ott-service/v2",
                "coca--r2d2--amk",
                null,
                "de.telekom.ei.listener.someone--else"));

    // WHEN
    var result =
        SpectreService.resolveTargetEventType(
            rules, "coca--r2d2--amk", "/a4m/content/ott-service/v2", null);

    // THEN: the first rule wins
    assertThat(result).contains(TARGET_EVENT_TYPE);
  }
}

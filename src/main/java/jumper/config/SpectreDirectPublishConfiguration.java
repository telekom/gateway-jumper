// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import java.util.List;
import jumper.model.config.SpectreDirectPublishRule;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * TEMPORARY (World Cup 2026 peak-load mitigation).
 *
 * <p>Holds the hardcoded set of {@link SpectreDirectPublishRule}s that let Jumper publish selected
 * Spectre events straight to a team-specific event type, bypassing the Horizon-Galaxy multiplex
 * step that becomes a bottleneck under peak load. This is a pure data holder; the matching logic
 * lives in {@code SpectreService}.
 *
 * <p>Bound to {@code jumper.spectre.direct-publish}. The rule set is intentionally small and
 * static; this whole mechanism must be removed once the Horizon-Galaxy performance issue is
 * resolved. See PS-05 in
 * https://wiki.telekom.de/spaces/DHEI/pages/4212169506/Decouple+Listener+Spectre+and+PubSub+Traffic
 */
@Configuration
@ConfigurationProperties(prefix = "jumper.spectre.direct-publish")
@Data
public class SpectreDirectPublishConfiguration {

  private List<SpectreDirectPublishRule> rules = List.of();
}

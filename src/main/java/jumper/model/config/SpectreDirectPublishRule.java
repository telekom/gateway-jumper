// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Data;

/**
 * TEMPORARY (World Cup 2026 peak-load mitigation).
 *
 * <p>Describes a single Spectre "direct-publish" rule. When an observed listener call matches this
 * rule, Jumper publishes the Spectre event straight to {@link #targetEventType} instead of the
 * generic {@code de.telekom.ei.listener} type. This bypasses the Horizon-Galaxy multiplex step (and
 * the {@code auto_event_route_post} republish round-trip), which is the bottleneck under peak load.
 *
 * <p>A rule matches when:
 *
 * <ul>
 *   <li>{@link #consumer} equals the request's consumer client id, and
 *   <li>{@link #apiBasePath} equals the request's API base path, and
 *   <li>{@link #provider} is either {@code null} (not constrained) or equals the listener's service
 *       owner / provider.
 * </ul>
 *
 * <p>This is a deliberately blunt, hardcoded mitigation. It must be removed once the Horizon-Galaxy
 * multiplexer performance issue is resolved. See PS-05 in
 * https://wiki.telekom.de/spaces/DHEI/pages/4212169506/Decouple+Listener+Spectre+and+PubSub+Traffic
 */
@Data
public class SpectreDirectPublishRule {

  /** API base path to match against the request's {@code apiBasePath} (exact match). */
  private String apiBasePath;

  /** Consumer client id to match against the request's consumer (exact match). */
  private String consumer;

  /**
   * Optional provider / service owner to match against the listener's service owner. When {@code
   * null}, the provider is not part of the match.
   */
  private String provider;

  /**
   * Target event type the matching Spectre event is published to directly, e.g. {@code
   * de.telekom.ei.listener.sh-fsf--codebuster--pacman-controlit}.
   */
  private String targetEventType;
}

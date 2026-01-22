// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import jumper.exception.LoadBalancingException;
import jumper.model.config.Server;

public final class LoadBalancingUtil {

  private LoadBalancingUtil() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static String calculateUpstream(@NotNull List<Server> servers) {
    // Sum total of weights
    double total = 0;
    for (Server server : servers) {
      total += server.getWeight();
    }

    // Check if all weights are zero
    if (total == 0) {
      throw new LoadBalancingException("can not calculate upstream");
    }

    // Random a number between [0, total)
    double random = Math.random() * total;

    // Seek cursor to find which area the random is in
    double cursor = 0;
    for (Server server : servers) {
      cursor += server.getWeight();
      if (cursor > random) {
        return server.getUpstream();
      }
    }

    throw new LoadBalancingException("can not calculate upstream");
  }
}

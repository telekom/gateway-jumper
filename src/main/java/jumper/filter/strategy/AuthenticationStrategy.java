// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.filter.strategy;

import jumper.filter.RequestProcessingContext;

/**
 * Strategy interface for handling different authentication scenarios in the Gateway Jumper. Each
 * implementation handles a specific authentication method (mesh tokens, basic auth, etc.).
 */
public interface AuthenticationStrategy {

  /**
   * Applies the authentication strategy to the request.
   *
   * @param context The request processing context containing all necessary data
   */
  void authenticate(RequestProcessingContext context);

  /**
   * Determines if this strategy can handle the given request context.
   *
   * @param context The request processing context
   * @return true if this strategy should be used for the request
   */
  boolean canHandle(RequestProcessingContext context);

  /**
   * Returns the name of this authentication strategy for logging purposes.
   *
   * @return strategy name
   */
  String getStrategyName();
}

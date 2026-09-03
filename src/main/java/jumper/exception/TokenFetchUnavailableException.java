// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class TokenFetchUnavailableException extends ResponseStatusException {

  public TokenFetchUnavailableException(String tokenEndpoint, Throwable cause) {
    super(
        HttpStatus.UNAUTHORIZED,
        "Failed to connect to " + tokenEndpoint + ", cause: " + cause.getMessage(),
        cause);
  }
}

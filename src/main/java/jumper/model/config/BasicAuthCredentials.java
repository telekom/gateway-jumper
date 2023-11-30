// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BasicAuthCredentials {
  private String username;
  private String password;
}

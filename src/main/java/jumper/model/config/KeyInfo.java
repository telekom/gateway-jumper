// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import java.security.PrivateKey;
import lombok.Data;

@Data
public class KeyInfo {

  private PrivateKey pk;
  private String kid;
}

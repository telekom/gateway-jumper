// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Data;
import lombok.ToString;

@Data
public class GatewayClient {
  private String id;

  @ToString.Exclude private String secret;
  private String issuer;
}

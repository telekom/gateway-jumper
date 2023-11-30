// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import lombok.Data;

@Data
public class RouteListener {
  private String issue;
  private String serviceOwner;
}

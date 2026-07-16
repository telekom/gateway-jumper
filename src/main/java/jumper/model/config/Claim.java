// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** A single configured token claim from the jumper_config {@code claims} blob. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Claim {
  private String key; // target JWT claim name (currently only "aud" is honoured)
  private String value; // literal value
  private String valueFrom; // runtime reference, currently only "ConsumerClientId"
}

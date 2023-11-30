// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Spectre {
  private String specversion;
  private String type; // listener.ei.telekom.de.listener
  private String source = "RouteListener";
  private UUID id;
  private String datacontenttype;
  private SpectreData data;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  String time;

  @JsonIgnore String spanId;
}

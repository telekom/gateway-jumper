// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditLog {
  private Date timestamp;
  private String consumer;
  private String consumerOriginZone;
  private String apiBasePath;
  private String upstreamPath;
}

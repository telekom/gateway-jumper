// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Date;
import jumper.model.config.AuditLog;
import jumper.model.request.IncomingTokenClaims;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuditLogService {

  public static void writeFailoverAuditLog(
      String finalUpstreamUri, String apiBasePath, IncomingTokenClaims incomingTokenClaims) {
    log.info(
        AuditLog.builder()
            .upstreamPath(finalUpstreamUri)
            .apiBasePath(apiBasePath)
            .consumer(incomingTokenClaims.clientId())
            .consumerOriginZone(incomingTokenClaims.originZone())
            .timestamp(new Date())
            .build()
            .toString());
  }
}

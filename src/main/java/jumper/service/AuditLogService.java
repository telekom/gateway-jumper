// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.util.Date;
import jumper.model.config.AuditLog;
import jumper.model.config.JumperConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuditLogService {
  public static void logInfo(String msg) {
    log.info(msg);
  }

  public static void writeFailoverAuditLog(JumperConfig jumperConfig) {
    logInfo(
        AuditLog.builder()
            .upstreamPath(jumperConfig.getFinalApiUrl())
            .apiBasePath(jumperConfig.getApiBasePath())
            .consumer(jumperConfig.getConsumer())
            .consumerOriginZone(jumperConfig.getConsumerOriginZone())
            .timestamp(new Date())
            .build()
            .toString());
  }
}

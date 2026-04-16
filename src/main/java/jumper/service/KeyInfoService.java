// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import jumper.model.config.KeyInfo;
import jumper.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeyInfoService {

  @Value("${jumper.security.dir}")
  private final String path;

  @Value("${jumper.security.pk-file}")
  private final String keyFile;

  @Value("${jumper.security.kid-file}")
  private final String kidFile;

  private final MeterRegistry meterRegistry;

  private final AtomicReference<KeyInfo> keyInfoRef = new AtomicReference<>();
  private final AtomicInteger keyInfoLoadStatus = new AtomicInteger(0);
  private final AtomicLong lastEventTimestamp = new AtomicLong(0);

  @PostConstruct
  public void init() {
    keyInfoRef.set(loadKeyInfo());
    Gauge.builder("jumper.keyinfo.load.status", () -> keyInfoLoadStatus)
        .description("KeyInfo load status (1=success, 0=failure)")
        .register(meterRegistry);
    Gauge.builder("jumper.keyinfo.load.last.timestamp", () -> lastEventTimestamp)
        .description("KeyInfo Unix timestamp of last update in seconds")
        .register(meterRegistry);
  }

  @Scheduled(fixedRateString = "${jumper.security.key-refresh-interval-ms}")
  public void refresh() {
    log.info("Refreshing KeyInfo from disk");
    keyInfoRef.set(loadKeyInfo());
  }

  public KeyInfo getKeyInfo() {
    return keyInfoRef.get();
  }

  private KeyInfo loadKeyInfo() {
    lastEventTimestamp.set(Instant.now().getEpochSecond());
    try {
      String kid = Files.readString(Path.of(path, kidFile));
      PrivateKey privateKey = RsaUtils.getPrivateKey(Path.of(path, keyFile));
      KeyInfo keyInfo = new KeyInfo();
      keyInfo.setKid(kid);
      keyInfo.setPk(privateKey);
      log.debug("KeyInfo loaded successfully, kid={}", kid);
      keyInfoLoadStatus.set(1);
      lastEventTimestamp.set(System.currentTimeMillis());
      return keyInfo;
    } catch (IOException | GeneralSecurityException e) {
      keyInfoLoadStatus.set(0);
      KeyInfo existing = keyInfoRef.get();
      if (existing != null) {
        log.error("Failed to refresh KeyInfo, keeping existing key", e);
        return existing;
      }
      throw new IllegalStateException("Failed to load KeyInfo on startup", e);
    }
  }
}

// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import jumper.model.config.KeyInfo;
import jumper.util.RsaUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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

  @Cacheable(value = "cache-key-info", cacheManager = "caffeineCacheManager")
  public KeyInfo getKeyInfo() throws IOException, GeneralSecurityException {
    String kid = getKid();
    PrivateKey privateKey = RsaUtils.getPrivateKey(Path.of(path, keyFile));

    KeyInfo keyInfo = new KeyInfo();
    keyInfo.setKid(kid);
    keyInfo.setPk(privateKey);

    return keyInfo;
  }

  private String getKid() throws IOException {
    return Files.readString(Path.of(path, kidFile));
  }
}

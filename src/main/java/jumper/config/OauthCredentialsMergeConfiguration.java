// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import jumper.model.config.JumperConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the rollout flag for merging consumer-specific oauth entries over the provider "default"
 * entry onto {@link JumperConfig}. The flag lives in a static field there because JumperConfig
 * instances are deserialized per request from the jumper_config header, outside Spring's reach.
 */
@Configuration
public class OauthCredentialsMergeConfiguration {

  public OauthCredentialsMergeConfiguration(
      @Value("${jumper.oauth.merge-consumer-with-default:true}") boolean mergeConsumerWithDefault) {
    JumperConfig.setMergeConsumerWithDefault(mergeConsumerWithDefault);
  }
}

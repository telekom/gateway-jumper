// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.time.Duration;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class CustomHttpClientConfiguration {

  @Value("${test.upstream.ssl.handshake-timeout}")
  private long handshakeTimeout;

  @Bean
  @Primary
  public HttpClientCustomizer httpClientCustomizer() throws SSLException {
    SslContext sslContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    return httpClient ->
        httpClient.secure(
            t -> t.sslContext(sslContext).handshakeTimeout(Duration.ofMillis(handshakeTimeout)));
  }
}

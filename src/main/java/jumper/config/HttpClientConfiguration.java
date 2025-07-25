// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.time.Duration;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class HttpClientConfiguration {

  @Value("${spring.cloud.oauth.connect-timeout:10000}")
  private int oauthConnectTimeout;

  @Value("${spring.cloud.oauth.pool.max-life-time:300}")
  private int oauthPoolMaxLifeTime;

  @Value("${spring.cloud.oauth.pool.max-idle-time:2}")
  private int oauthPoolMaxIdleTime;

  @Value("${spring.cloud.oauth.pool.metrics:true}")
  private boolean oauthPoolMetrics;

  private final HttpClientProperties properties;
  private final TlsHardeningConfiguration tlsHardeningConfiguration;

  @Bean
  public HttpClientCustomizer httpClientCustomizer() throws SSLException {
    SslContext sslContext = createSslContextWithCustomizedCiphers();
    return httpClient -> httpClient.secure(t -> t.sslContext(sslContext));
  }

  @Bean("spectreServiceWebClient")
  public WebClient createWebClientForSpectreService(WebClient.Builder webClientBuilder) {
    return webClientBuilder.build();
  }

  @Bean("oauthTokenUtilWebClient")
  public WebClient createWebClientForOauthTokenUtil(WebClient.Builder webClientBuilder)
      throws SSLException {
    SslContext sslContext = createSslContextWithCustomizedCiphers();
    HttpClient httpClient =
        HttpClient.create(getProvider())
            .secure(t -> t.sslContext(sslContext))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, oauthConnectTimeout);
    httpClient = configureProxy(httpClient);

    return webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }

  private SslContext createSslContextWithCustomizedCiphers() throws SSLException {

    if (tlsHardeningConfiguration.getDefaultAllowedCipherSuites() == null
        || tlsHardeningConfiguration.getDefaultAllowedCipherSuites().isEmpty()) {
      throw new SSLException("allowedCipherSuites must not be empty, check configuration");
    }

    return SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .protocols("TLSv1.2", "TLSv1.3")
        .sslProvider(SslProvider.JDK)
        .ciphers(
            Stream.concat(
                    tlsHardeningConfiguration.getDefaultAllowedCipherSuites().stream(),
                    tlsHardeningConfiguration.getAdditionalAllowedCipherSuites().stream())
                .distinct()
                .toList())
        .build();
  }

  private HttpClient configureProxy(HttpClient httpClient) {

    // configure proxy only if proxy host is set.
    if (StringUtils.isNotBlank((properties.getProxy().getHost()))) {
      log.info("Configuring Proxy: {}", properties.getProxy());
      HttpClientProperties.Proxy proxyProperties = properties.getProxy();
      httpClient =
          httpClient.proxy(proxySpec -> configureProxyProvider(proxyProperties, proxySpec));
    }

    // otherwise return httpClient as it is...
    return httpClient;
  }

  private ProxyProvider.Builder configureProxyProvider(
      HttpClientProperties.Proxy proxyProperties, ProxyProvider.TypeSpec proxySpec) {

    ProxyProvider.Builder builder =
        proxySpec.type(proxyProperties.getType()).host(proxyProperties.getHost());

    PropertyMapper map = PropertyMapper.get();
    map.from(proxyProperties::getPort).whenNonNull().to(builder::port);
    map.from(proxyProperties::getNonProxyHostsPattern).whenHasText().to(builder::nonProxyHosts);

    return builder;
  }

  private ConnectionProvider getProvider() {
    return ConnectionProvider.builder("oauth")
        .maxConnections(100)
        .maxIdleTime(Duration.ofSeconds(oauthPoolMaxIdleTime))
        .maxLifeTime(Duration.ofSeconds(oauthPoolMaxLifeTime))
        .pendingAcquireMaxCount(-1)
        .metrics(oauthPoolMetrics)
        .build();
  }
}

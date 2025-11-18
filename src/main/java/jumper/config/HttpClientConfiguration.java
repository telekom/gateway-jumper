// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.security.KeyStore;
import java.time.Duration;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
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

  @Value("${jumper.ssl.certificate-validation-mode:warn}")
  private String certificateValidationMode;

  private final HttpClientProperties properties;
  private final TlsHardeningConfiguration tlsHardeningConfiguration;
  private final MeterRegistry meterRegistry;

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
        .trustManager(createTrustManager())
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

  /**
   * Creates a trust manager based on the configured validation mode.
   *
   * @return X509TrustManager instance
   * @throws SSLException if trust manager creation fails
   */
  private X509TrustManager createTrustManager() throws SSLException {
    try {
      switch (certificateValidationMode.toLowerCase()) {
        case "strict":
          log.info(
              "Certificate validation mode: STRICT - connections will fail on invalid"
                  + " certificates");
          // Use default trust manager (validates against system CA certificates)
          TrustManagerFactory tmf =
              TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
          tmf.init((KeyStore) null);
          return (X509TrustManager) tmf.getTrustManagers()[0];

        case "warn":
          log.info(
              "Certificate validation mode: WARN - invalid certificates will be logged but"
                  + " connections allowed");
          // Use warning trust manager (validates but only logs warnings and records metrics)
          TrustManagerFactory defaultTmf =
              TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
          defaultTmf.init((KeyStore) null);
          X509TrustManager defaultTrustManager =
              (X509TrustManager) defaultTmf.getTrustManagers()[0];
          return new WarningTrustManager(defaultTrustManager, meterRegistry);

        case "insecure":
        default:
          log.warn(
              "Certificate validation mode: INSECURE - all certificates accepted without validation"
                  + " (NOT RECOMMENDED FOR PRODUCTION)");
          // Use insecure trust manager (accepts all certificates)
          return (X509TrustManager) InsecureTrustManagerFactory.INSTANCE.getTrustManagers()[0];
      }
    } catch (Exception e) {
      throw new SSLException("Failed to create trust manager", e);
    }
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

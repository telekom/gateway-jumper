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
import java.util.List;
import java.util.stream.Stream;
import javax.net.ssl.SSLException;
import lombok.RequiredArgsConstructor;
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
public class HttpClientConfiguration {

  @Value("${CUSTOM_CIPHERS:}")
  List<String> customCiphers;

  private final HttpClientProperties properties;

  @Bean
  public HttpClientCustomizer httpClientCustomizer() throws SSLException {
    SslContext sslContext = createSslContextWithCustomizedCiphers();
    return httpClient -> httpClient.secure(t -> t.sslContext(sslContext));
  }

  @Bean("spectreServiceWebClient")
  public WebClient createWebClientForSpectreService() {
    return WebClient.create();
  }

  @Bean("oauthTokenUtilWebClient")
  public WebClient createWebClientForOauthTokenUtil() throws SSLException {
    SslContext sslContext = createSslContextWithCustomizedCiphers();
    HttpClient httpClient =
        HttpClient.create(getProvider())
            .secure(t -> t.sslContext(sslContext))
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);
    httpClient = configureProxy(httpClient);

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
  }

  private SslContext createSslContextWithCustomizedCiphers() throws SSLException {

    List<String> dtCiphers =
        List.of(
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256"
            // ,"TLS_ECDHE_ECDSA_WITH_AES_256_CCM"
            // ,"TLS_DHE_RSA_WITH_AES_256_CCM"
            ,
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256"
            // ,"TLS_ECDHE_ECDSA_WITH_AES_128_CCM"
            // ,"TLS_DHE_RSA_WITH_AES_128_CCM"
            ,
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_GCM_SHA256"
            // ,"TLS_AES_128_CCM_SHA256"
            );

    return SslContextBuilder.forClient()
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .protocols("TLSv1.2", "TLSv1.3")
        .sslProvider(SslProvider.JDK)
        .ciphers(Stream.concat(dtCiphers.stream(), customCiphers.stream()).distinct().toList())
        .build();
  }

  private HttpClient configureProxy(HttpClient httpClient) {

    // configure proxy only if proxy host is set.
    if (StringUtils.isNotBlank((properties.getProxy().getHost()))) {
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
        .maxIdleTime(Duration.ofSeconds(5))
        .maxLifeTime(Duration.ofSeconds(60))
        .pendingAcquireMaxCount(-1)
        .build();
  }
}

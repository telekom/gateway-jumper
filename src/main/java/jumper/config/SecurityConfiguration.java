// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import io.micrometer.tracing.Tracer;
import jumper.exception.LoggingServerExchangeRejectedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.firewall.ServerExchangeRejectedHandler;
import org.springframework.security.web.server.firewall.StrictServerWebExchangeFirewall;
import org.springframework.web.server.session.WebSessionManager;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

    return http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .build();
  }

  /**
   * Configures a custom ServerWebExchangeFirewall that allows semicolons in URLs.
   *
   * <p>Some applications use semicolons for matrix parameters. This is also discussed in RFC 3986.
   * Semicolons are thus legitimate in URI paths and we need to support them.
   *
   * <p>The default StrictServerWebExchangeFirewall blocks them as potentially malicious, but we
   * need to support them for proper URI handling.
   *
   * @return StrictServerWebExchangeFirewall configured to allow semicolons and URL encoded slashes
   * @see <a
   *     href="https://docs.spring.io/spring-framework/reference/web/webflux/controller/ann-methods/matrix-variables.html">Matrix
   *     Variables in Spring Framework Reference</a>
   * @see <a
   *     href="https://docs.spring.io/spring-security/reference/reactive/exploits/firewall.html">Spring
   *     Security Firewall Documentation</a>
   */
  @Bean
  public StrictServerWebExchangeFirewall httpFirewall() {
    StrictServerWebExchangeFirewall firewall = new StrictServerWebExchangeFirewall();
    firewall.setAllowSemicolon(true);
    firewall.setAllowUrlEncodedSlash(true);
    firewall.setAllowUrlEncodedDoubleSlash(true);
    firewall.setAllowUrlEncodedPercent(true);
    firewall.setAllowUrlEncodedPeriod(true);

    return firewall;
  }

  /**
   * Replaces Spring Security's default exchange rejection handling, which answers {@code 400 Bad
   * Request} but only logs at {@code DEBUG} and bypasses {@link
   * jumper.exception.JsonErrorWebExceptionHandler}.
   *
   * @param tracer used to attach a rejection event and reason tag to the current span
   * @return handler that keeps the 400 response but logs at WARN and records the rejection on the
   *     current observation
   */
  @Bean
  public ServerExchangeRejectedHandler serverExchangeRejectedHandler(Tracer tracer) {
    return new LoggingServerExchangeRejectedHandler(tracer);
  }

  /**
   * Configures a stateless WebSessionManager that prevents session creation. This gateway operates
   * in a completely stateless manner - all authentication is handled via tokens in custom filters.
   *
   * <p>This implementation returns Mono.empty() to ensure no sessions are ever created, preventing
   * memory overhead and ensuring true stateless operation.
   *
   * <p>WARNING: If any code attempts to call exchange.getSession(), it will fail. All request state
   * must be passed via headers or request attributes.
   *
   * @return WebSessionManager that prevents session creation
   */
  @Bean
  public WebSessionManager webSessionManager() {
    return exchange -> Mono.empty();
  }
}

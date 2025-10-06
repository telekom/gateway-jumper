// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
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

package br.com.mentoria.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class AccountSecurityConfig {

    @Bean
    SecurityFilterChain jwksSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(AccountLoginController.JWKS, AccountLoginController.WELL_KNOWN_JWKS)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, AccountLoginController.JWKS, AccountLoginController.WELL_KNOWN_JWKS)
                        .permitAll())
                .build();
    }
}

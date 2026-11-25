package br.com.mentoria.gateway.security;

import java.time.Duration;
import java.util.List;

import br.com.mentoria.gateway.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class SecurityConfig {

    static final String AUTH_PATHS = "/api/v1/auth/**";
    static final String ROLE_CLAIM = "role";

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> { })
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .pathMatchers(AUTH_PATHS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers("/api/v1/student/**").hasRole("STUDENT")
                        .pathMatchers("/api/v1/teacher/**").hasRole("TEACHER")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(ignoringPublicPaths())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleAuthenticationConverter())))
                .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withJwkSetUri(properties.jwt().jwkSetUri())
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Idempotency-Key"));
        config.setExposedHeaders(List.of("Location", "Retry-After", "X-Correlation-Id",
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Correction-Remaining"));
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static ReactiveJwtAuthenticationConverterAdapter roleAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private static ServerBearerTokenAuthenticationConverter ignoringPublicPaths() {
        AntPathMatcher matcher = new AntPathMatcher();
        return new ServerBearerTokenAuthenticationConverter() {
            @Override
            public Mono<org.springframework.security.core.Authentication> convert(
                    org.springframework.web.server.ServerWebExchange exchange) {
                if (matcher.match(AUTH_PATHS, exchange.getRequest().getPath().value())) {
                    return Mono.empty();
                }
                return super.convert(exchange);
            }
        };
    }
}

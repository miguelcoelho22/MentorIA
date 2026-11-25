package br.com.mentoria.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(token -> withIdentity(exchange, token))
                .defaultIfEmpty(withIdentity(exchange, null))
                .flatMap(chain::filter);
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, JwtAuthenticationToken token) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID);
                    headers.remove(USER_ROLE);
                    if (token != null) {
                        headers.set(USER_ID, token.getToken().getSubject());
                        headers.set(USER_ROLE, token.getToken().getClaimAsString(SecurityConfig.ROLE_CLAIM));
                    }
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

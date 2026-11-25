package br.com.mentoria.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;

import br.com.mentoria.gateway.GatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Bean
    @Primary
    InMemoryRateLimiter generalRateLimiter(GatewayProperties properties) {
        return new InMemoryRateLimiter(properties.rateLimit().generalPerMinute(), MINUTE,
                "X-RateLimit", Clock.systemUTC());
    }

    @Bean
    InMemoryRateLimiter correctionRateLimiter(GatewayProperties properties) {
        return new InMemoryRateLimiter(properties.rateLimit().correctionPerMinute(), MINUTE,
                "X-RateLimit-Correction", Clock.systemUTC());
    }
}

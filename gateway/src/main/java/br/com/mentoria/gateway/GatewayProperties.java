package br.com.mentoria.gateway;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mentoria.gateway")
public record GatewayProperties(Jwt jwt, Cors cors, RateLimit rateLimit) {

    public record Jwt(String jwkSetUri, String issuer) { }

    public record Cors(List<String> allowedOrigins) { }

    public record RateLimit(int generalPerMinute, int correctionPerMinute) { }
}

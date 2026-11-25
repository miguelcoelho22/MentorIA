package br.com.mentoria.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import reactor.core.publisher.Mono;

public final class InMemoryRateLimiter implements RateLimiter<Object> {

    private final int limit;
    private final long periodMillis;
    private final String headerPrefix;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastEviction;

    public InMemoryRateLimiter(int limit, Duration period, String headerPrefix, Clock clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("O limite deve ser positivo.");
        }
        this.limit = limit;
        this.periodMillis = period.toMillis();
        this.headerPrefix = headerPrefix;
        this.clock = clock;
        this.lastEviction = new AtomicLong(clock.millis());
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        long now = clock.millis();
        evictFullBuckets(now);

        Decision decision = new Decision();
        buckets.compute(id, (key, bucket) -> {
            Bucket current = bucket == null ? new Bucket(limit, now) : bucket.refill(now, limit, periodMillis);
            if (current.tokens >= 1) {
                decision.allowed = true;
                current = new Bucket(current.tokens - 1, now);
            } else {
                double missing = 1 - current.tokens;
                decision.retryAfterSeconds = (long) Math.ceil(missing * periodMillis / limit / 1000.0);
            }
            decision.remaining = (long) Math.floor(current.tokens);
            return current;
        });

        Map<String, String> headers = decision.allowed
                ? Map.of(headerPrefix + "-Limit", String.valueOf(limit),
                        headerPrefix + "-Remaining", String.valueOf(decision.remaining))
                : Map.of(headerPrefix + "-Limit", String.valueOf(limit),
                        headerPrefix + "-Remaining", "0",
                        "Retry-After", String.valueOf(Math.max(1, decision.retryAfterSeconds)));
        return Mono.just(new Response(decision.allowed, headers));
    }

    private void evictFullBuckets(long now) {
        long last = lastEviction.get();
        if (now - last >= periodMillis && lastEviction.compareAndSet(last, now)) {
            buckets.entrySet().removeIf(entry -> now - entry.getValue().updatedAt >= periodMillis);
        }
    }

    @Override
    public Map<String, Object> getConfig() {
        return Map.of();
    }

    @Override
    public Class<Object> getConfigClass() {
        return Object.class;
    }

    @Override
    public Object newConfig() {
        return new Object();
    }

    private record Bucket(double tokens, long updatedAt) {

        Bucket refill(long now, int limit, long periodMillis) {
            double refilled = tokens + (now - updatedAt) * (double) limit / periodMillis;
            return new Bucket(Math.min(limit, refilled), now);
        }
    }

    private static final class Decision {
        boolean allowed;
        long remaining;
        long retryAfterSeconds;
    }
}

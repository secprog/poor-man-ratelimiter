package com.example.gateway.ratelimit;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.service.AnalyticsService;
import com.example.gateway.service.PolicyService;
import com.example.gateway.store.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component("customRedisRateLimiter")
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter implements RateLimiter<RedisRateLimiter.Config> {

    private static final String REMAINING_FIELD = "remaining";
    private static final String LAST_REFILL_FIELD = "lastRefill";
    private static final Duration STATE_TTL = Duration.ofHours(24);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final PolicyService policyService;
    private final AnalyticsService analyticsService;

    public static class Config {
    }

    @Override
    public Class<Config> getConfigClass() {
        return Config.class;
    }

    @Override
    public Config newConfig() {
        return new Config();
    }

    @Override
    public Map<String, Config> getConfig() {
        return Collections.emptyMap();
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        String[] keys = id.split(";");

        if (keys.length == 0 || (keys.length == 1 && !keys[0].contains(":"))) {
            return Mono.just(new Response(true, Collections.emptyMap()));
        }

        return Flux.fromArray(keys)
                .flatMap(key -> {
                    String[] parts = key.split(":", 3);
                    if (parts.length < 2) {
                        return Mono.empty();
                    }

                    Long policyId;
                    try {
                        policyId = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        return Mono.empty();
                    }

                    return policyService.getAllPolicies()
                            .filter(p -> p.getPolicyId() != null && p.getPolicyId().equals(policyId))
                            .next()
                            .flatMap(policy -> checkLimit(key, policy));
                })
                .collectList()
                .map(responses -> responses.stream()
                        .filter(r -> !r.isAllowed())
                        .findFirst()
                        .orElseGet(() -> responses.isEmpty()
                                ? new Response(true, Collections.emptyMap())
                                : responses.get(0)));
    }

    private Mono<Response> checkLimit(String key, RateLimitPolicy policy) {
        int rate = policy.getReplenishRate() != null ? policy.getReplenishRate() : 10;
        int burst = policy.getBurstCapacity() != null ? policy.getBurstCapacity() : 20;
        Instant now = Instant.now();
        String stateKey = RedisKeys.rateLimitStateKey(key);

        return hashOps()
                .multiGet(stateKey, List.of(REMAINING_FIELD, LAST_REFILL_FIELD))
                .defaultIfEmpty(List.of(null, null))
                .flatMap(values -> {
                    int remaining = parseInt(values, 0, burst);
                    Instant lastRefill = parseInstant(values, 1, now);

                    long elapsedSeconds = ChronoUnit.SECONDS.between(lastRefill, now);
                    int tokensToAdd = (int) (elapsedSeconds * rate);
                    int newTokens = Math.min(burst, remaining + tokensToAdd);

                    boolean allowed = newTokens >= 1;
                    int updatedRemaining = allowed ? newTokens - 1 : newTokens;

                    if (allowed) {
                        analyticsService.incrementAllowed();
                    } else {
                        analyticsService.incrementBlocked();
                    }

                    return updateState(stateKey, updatedRemaining, now)
                            .thenReturn(new Response(allowed, buildHeaders(updatedRemaining, burst)));
                })
                .onErrorResume(e -> {
                    log.error("Rate limit check failed for key {}: {}", key, e.getMessage());
                    return Mono.just(new Response(true, Collections.emptyMap()));
                });
    }

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    private Mono<Void> updateState(String key, int remaining, Instant now) {
        return hashOps()
                .putAll(key, Map.of(
                        REMAINING_FIELD, String.valueOf(remaining),
                        LAST_REFILL_FIELD, String.valueOf(now.toEpochMilli())))
                .then(redisTemplate.expire(key, STATE_TTL))
                .then();
    }

    private int parseInt(List<String> values, int index, int fallback) {
        if (values == null || values.size() <= index) {
            return fallback;
        }
        String value = values.get(index);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Instant parseInstant(List<String> values, int index, Instant fallback) {
        if (values == null || values.size() <= index) {
            return fallback;
        }
        String value = values.get(index);
        if (value == null) {
            return fallback;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Map<String, String> buildHeaders(int remaining, int burst) {
        return Map.of(
                "X-RateLimit-Remaining", String.valueOf(remaining),
                "X-RateLimit-Limit", String.valueOf(burst));
    }
}

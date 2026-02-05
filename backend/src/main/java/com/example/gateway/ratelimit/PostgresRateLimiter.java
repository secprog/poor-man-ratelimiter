package com.example.gateway.ratelimit;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.service.AnalyticsService;
import com.example.gateway.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;

@Component("postgresRateLimiter")
@RequiredArgsConstructor
@Slf4j
public class PostgresRateLimiter implements RateLimiter<PostgresRateLimiter.Config> {

    private final DatabaseClient databaseClient;
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
        // Split composite key (e.g. "1:IP:ip1;2:USER:user1")
        String[] keys = id.split(";");

        // If single key, use fast path (or if default/error)
        if (keys.length == 0 || (keys.length == 1 && !keys[0].contains(":"))) {
            return Mono.just(new Response(true, Collections.emptyMap()));
        }

        // Process all keys. We need to check allow for ALL of them.
        // If any deny, we deny.
        // We accumulate the headers (min remaining, etc)
        // CHALLENGE: We should decrement all of them if allowed.
        // Simplified approach: Serialize checks. If one fails, stop?
        // No, typically we fetch token state for all, verify sufficiency, then commit.

        return Flux.fromArray(keys)
                .flatMap(key -> {
                    String[] parts = key.split(":", 3); // id:type:key_value
                    if (parts.length < 2)
                        return Mono.empty();

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
                .map(responses -> {
                    boolean allAllowed = responses.stream().allMatch(Response::isAllowed);
                    // Merge headers (e.g. take min remaining tokens)
                    // This is a naive merge. Ideally we report the limit that blocked.
                    return responses.stream()
                            .filter(r -> !r.isAllowed())
                            .findFirst()
                            .orElseGet(() -> responses.isEmpty() ? new Response(true, Collections.emptyMap())
                                    : responses.get(0));
                });
    }

    private Mono<Response> checkLimit(String key, RateLimitPolicy policy) {
        int rate = policy.getReplenishRate() != null ? policy.getReplenishRate() : 10;
        int burst = policy.getBurstCapacity() != null ? policy.getBurstCapacity() : 20;
        Instant now = Instant.now();

        // Try to get existing state
        return databaseClient
                .sql("SELECT remaining_tokens, last_refill_time FROM rate_limit_state WHERE limit_key = :key")
                .bind("key", key)
                .map((row, metadata) -> {
                    Integer remaining = row.get("remaining_tokens", Integer.class);
                    Instant lastRefill = row.get("last_refill_time", Instant.class);
                    return new TokenState(remaining != null ? remaining : burst, lastRefill != null ? lastRefill : now);
                })
                .one()
                .defaultIfEmpty(new TokenState(burst, now))
                .flatMap(state -> {
                    // Calculate tokens to add based on elapsed time
                    long elapsedSeconds = ChronoUnit.SECONDS.between(state.lastRefill, now);
                    int tokensToAdd = (int) (elapsedSeconds * rate);
                    int newTokens = Math.min(burst, state.remaining + tokensToAdd);

                    if (newTokens >= 1) {
                        // Allow and decrement
                        analyticsService.incrementAllowed();
                        int remaining = newTokens - 1;
                        return upsertState(key, remaining, now)
                                .thenReturn(new Response(true, buildHeaders(remaining, burst)));
                    } else {
                        // Deny
                        analyticsService.incrementBlocked();
                        return upsertState(key, newTokens, now)
                                .thenReturn(new Response(false, buildHeaders(newTokens, burst)));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Rate limit check failed for key {}: {}", key, e.getMessage());
                    return Mono.just(new Response(true, Collections.emptyMap())); // Fail open
                });
    }

    private Mono<Void> upsertState(String key, int remaining, Instant now) {
        return databaseClient.sql(
                "INSERT INTO rate_limit_state (limit_key, remaining_tokens, last_refill_time) " +
                        "VALUES (:key, :remaining, :now) " +
                        "ON CONFLICT (limit_key) DO UPDATE SET remaining_tokens = :remaining, last_refill_time = :now")
                .bind("key", key)
                .bind("remaining", remaining)
                .bind("now", now)
                .then();
    }

    private Map<String, String> buildHeaders(int remaining, int burst) {
        return Map.of(
                "X-RateLimit-Remaining", String.valueOf(remaining),
                "X-RateLimit-Limit", String.valueOf(burst));
    }

    private record TokenState(int remaining, Instant lastRefill) {
    }
}

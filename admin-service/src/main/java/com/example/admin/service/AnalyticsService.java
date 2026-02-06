package com.example.admin.service;

import com.example.admin.model.AnalyticsUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String ANALYTICS_KEY_PREFIX = "analytics:";
    private static final String REQUESTS_ALLOWED_KEY = "analytics:requests_allowed";
    private static final String REQUESTS_BLOCKED_KEY = "analytics:requests_blocked";

    public Mono<AnalyticsUpdate> getLatestUpdate() {
        return Mono.zip(
                getRequestsAllowed(),
                getRequestsBlocked(),
                getActivePoliciesCount()
        ).map(tuple -> new AnalyticsUpdate(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    private Mono<Long> getRequestsAllowed() {
        return redisTemplate.opsForValue().get(REQUESTS_ALLOWED_KEY)
                .map(Long::parseLong)
                .onErrorReturn(0L)
                .defaultIfEmpty(0L);
    }

    private Mono<Long> getRequestsBlocked() {
        return redisTemplate.opsForValue().get(REQUESTS_BLOCKED_KEY)
                .map(Long::parseLong)
                .onErrorReturn(0L)
                .defaultIfEmpty(0L);
    }

    private Mono<Integer> getActivePoliciesCount() {
        return redisTemplate.opsForHash().size("rate_limit_rules")
                .map(Long::intValue);
    }

    public Mono<Void> recordAllowed() {
        return redisTemplate.opsForValue().increment(REQUESTS_ALLOWED_KEY)
                .then();
    }

    public Mono<Void> recordBlocked() {
        return redisTemplate.opsForValue().increment(REQUESTS_BLOCKED_KEY)
                .then();
    }
}

package com.example.admin.service;

import com.example.admin.model.RateLimitRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitRuleStore {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String RULES_HASH_KEY = "rate_limit_rules";

    public Mono<RateLimitRule> save(RateLimitRule rule) {
        return Mono.defer(() -> {
            try {
                String json = objectMapper.writeValueAsString(rule);
                return redisTemplate.opsForHash().put(RULES_HASH_KEY, rule.getId().toString(), json)
                        .then(Mono.just(rule))
                        .doOnSuccess(saved -> log.info("Saved rate limit rule: {}", saved.getId()));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    public Mono<RateLimitRule> findById(UUID id) {
        return redisTemplate.opsForHash().get(RULES_HASH_KEY, id.toString())
                .flatMap(obj -> Mono.defer(() -> {
                    try {
                        String json = (String) obj;
                        return Mono.just(objectMapper.readValue(json, RateLimitRule.class));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                }));
    }

    public Flux<RateLimitRule> findAll() {
        return redisTemplate.opsForHash().values(RULES_HASH_KEY)
                .flatMap(obj -> Mono.defer(() -> {
                    try {
                        String json = (String) obj;
                        return Mono.just(objectMapper.readValue(json, RateLimitRule.class));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                }));
    }

    public Flux<RateLimitRule> findByActiveTrue() {
        return findAll()
                .filter(rule -> Boolean.TRUE.equals(rule.getActive()));
    }

    public Mono<Void> deleteById(UUID id) {
        return redisTemplate.opsForHash().remove(RULES_HASH_KEY, id.toString())
                .then()
                .doOnSuccess(v -> log.info("Deleted rate limit rule: {}", id));
    }
}

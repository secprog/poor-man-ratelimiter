package com.example.admin.service;

import com.example.admin.model.SystemConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigStore {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private static final String CONFIG_HASH_KEY = "system_config";

    public Mono<SystemConfig> save(String key, String value) {
        return redisTemplate.opsForHash().put(CONFIG_HASH_KEY, key, value)
                .then(Mono.just(new SystemConfig(key, value)))
                .doOnSuccess(saved -> log.info("Saved system config: {} = {}", key, value));
    }

    public Mono<String> getString(String key, String defaultValue) {
        return redisTemplate.opsForHash().get(CONFIG_HASH_KEY, key)
                .map(obj -> (String) obj)
                .defaultIfEmpty(defaultValue);
    }

    public Mono<Integer> getInt(String key, Integer defaultValue) {
        return getString(key, null)
                .flatMap(value -> {
                    if (value == null) {
                        return Mono.just(defaultValue);
                    }
                    try {
                        return Mono.just(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        return Mono.just(defaultValue);
                    }
                });
    }

    public Mono<Boolean> getBoolean(String key, Boolean defaultValue) {
        return getString(key, null)
                .map(value -> value == null ? defaultValue : Boolean.parseBoolean(value));
    }

    public Flux<SystemConfig> findAll() {
        return redisTemplate.opsForHash().entries(CONFIG_HASH_KEY)
                .map(entry -> new SystemConfig((String) entry.getKey(), (String) entry.getValue()));
    }

    public Mono<Void> delete(String key) {
        return redisTemplate.opsForHash().remove(CONFIG_HASH_KEY, key)
                .then()
                .doOnSuccess(v -> log.info("Deleted system config: {}", key));
    }
}

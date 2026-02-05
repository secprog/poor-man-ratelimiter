package com.example.gateway.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteDefinitionStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    public Flux<RouteDefinition> findAll() {
        return hashOps()
                .values(RedisKeys.ROUTE_DEFINITIONS_HASH)
                .flatMap(this::deserialize);
    }

    public Mono<RouteDefinition> findById(String id) {
        return hashOps()
                .get(RedisKeys.ROUTE_DEFINITIONS_HASH, id)
                .flatMap(this::deserialize);
    }

    public Mono<RouteDefinition> save(RouteDefinition definition) {
        if (definition.getId() == null || definition.getId().isBlank()) {
            return Mono.error(new IllegalArgumentException("Route definition id must be set"));
        }
        return serialize(definition)
                .flatMap(json -> hashOps()
                        .put(RedisKeys.ROUTE_DEFINITIONS_HASH, definition.getId(), json)
                        .thenReturn(definition));
    }

    public Mono<Void> deleteById(String id) {
        return hashOps()
                .remove(RedisKeys.ROUTE_DEFINITIONS_HASH, id)
                .then();
    }

    private Mono<String> serialize(RouteDefinition definition) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(definition));
    }

    private Mono<RouteDefinition> deserialize(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, RouteDefinition.class))
                .onErrorResume(error -> {
                    log.warn("Failed to deserialize route definition: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}

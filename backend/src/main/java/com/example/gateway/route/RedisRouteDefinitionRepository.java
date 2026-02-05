package com.example.gateway.route;

import com.example.gateway.store.RouteDefinitionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisRouteDefinitionRepository implements RouteDefinitionRepository {

    private final RouteDefinitionStore store;

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        return store.findAll();
    }

    @Override
    public Mono<Void> save(Mono<RouteDefinition> route) {
        return route.flatMap(store::save).then();
    }

    @Override
    public Mono<Void> delete(Mono<String> routeId) {
        return routeId
                .flatMap(store::deleteById)
                .onErrorResume(error -> {
                    log.warn("Failed to delete route definition: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}

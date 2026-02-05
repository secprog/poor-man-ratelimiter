package com.example.gateway.service;

import com.example.gateway.model.RateLimitRule;
import com.example.gateway.store.RouteDefinitionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteSyncService {

    private final RouteDefinitionStore routeStore;
    private final ApplicationEventPublisher eventPublisher;

    public Mono<Void> syncRule(RateLimitRule rule) {
        if (rule == null || rule.getId() == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule id is required"));
        }

        if (!rule.isActive()) {
            return deleteRule(rule.getId());
        }

        String targetUri = rule.getTargetUri();
        if (targetUri == null || targetUri.isBlank()) {
            if (isGlobalRule(rule)) {
                return deleteRule(rule.getId());
            }
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetUri is required for active rules"));
        }

        RouteDefinition definition = buildDefinition(rule);
        return routeStore.save(definition)
                .doOnSuccess(saved -> {
                    log.info("Synced route for rule {} -> {}", rule.getId(), targetUri);
                    eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                })
                .then();
    }

    public Mono<Void> deleteRule(UUID ruleId) {
        String routeId = routeIdForRule(ruleId);
        return routeStore.deleteById(routeId)
                .doOnSuccess(ignored -> eventPublisher.publishEvent(new RefreshRoutesEvent(this)))
                .then();
    }

    private RouteDefinition buildDefinition(RateLimitRule rule) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(routeIdForRule(rule.getId()));
        definition.setUri(URI.create(rule.getTargetUri()));
        definition.setPredicates(List.of(pathPredicate(rule.getPathPattern())));
        definition.setOrder(rule.getPriority());
        return definition;
    }

    private PredicateDefinition pathPredicate(String pattern) {
        PredicateDefinition predicate = new PredicateDefinition();
        predicate.setName("Path");
        predicate.addArg("_genkey_0", pattern);
        return predicate;
    }

    private String routeIdForRule(UUID ruleId) {
        return "rule-" + ruleId;
    }

    private boolean isGlobalRule(RateLimitRule rule) {
        String pattern = rule.getPathPattern();
        return pattern != null && pattern.trim().equals("/**");
    }
}

package com.example.admin.controller;

import com.example.admin.model.RateLimitRule;
import com.example.admin.service.RateLimitRuleStore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/poormansRateLimit/api/admin/rules")
@RequiredArgsConstructor
@Slf4j
public class RateLimitRuleController {

    private final RateLimitRuleStore ruleStore;

    @GetMapping
    public Flux<RateLimitRule> getAllRules() {
        return ruleStore.findAll();
    }

    @GetMapping("/active")
    public Flux<RateLimitRule> getActiveRules() {
        return ruleStore.findByActiveTrue();
    }

    @GetMapping("/{id}")
    public Mono<RateLimitRule> getRuleById(@PathVariable UUID id) {
        return ruleStore.findById(id);
    }

    @PostMapping
    public Mono<RateLimitRule> createRule(@RequestBody RateLimitRule rule) {
        return Mono.defer(() -> {
            if (rule.getId() == null) {
                rule.setId(UUID.randomUUID());
            }
            return ruleStore.save(rule)
                    .doOnSuccess(saved -> log.info("Created new rate limit rule: {}", saved));
        });
    }

    @PutMapping("/{id}")
    public Mono<RateLimitRule> updateRule(@PathVariable UUID id, @RequestBody RateLimitRule rule) {
        return ruleStore.findById(id)
                .flatMap(existing -> {
                    rule.setId(id);
                    return ruleStore.save(rule)
                            .doOnSuccess(updated -> log.info("Updated rate limit rule: {}", updated));
                });
    }

    @PatchMapping("/{id}/queue")
    public Mono<RateLimitRule> updateQueueSettings(
            @PathVariable UUID id,
            @RequestBody QueueConfig queueConfig) {

        return ruleStore.findById(id)
                .flatMap(rule -> {
                    rule.setQueueEnabled(queueConfig.queueEnabled);
                    rule.setMaxQueueSize(queueConfig.maxQueueSize);
                    rule.setDelayPerRequestMs(queueConfig.delayPerRequestMs);

                    return ruleStore.save(rule)
                            .doOnSuccess(updated -> {
                                log.info("Updated queue settings for rule {}: enabled={}, maxSize={}, delayMs={}",
                                        id, queueConfig.queueEnabled, queueConfig.maxQueueSize,
                                        queueConfig.delayPerRequestMs);
                            });
                });
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteRule(@PathVariable UUID id) {
        return ruleStore.deleteById(id);
    }

    @PostMapping("/refresh")
    public Mono<Void> refreshRules() {
        return Mono.fromRunnable(() -> log.info("Rules refreshed from Redis"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueConfig {
        private Boolean queueEnabled;
        private Integer maxQueueSize;
        private Integer delayPerRequestMs;
    }
}


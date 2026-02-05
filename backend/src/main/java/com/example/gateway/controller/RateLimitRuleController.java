package com.example.gateway.controller;

import com.example.gateway.model.RateLimitRule;
import com.example.gateway.repository.RateLimitRuleRepository;
import com.example.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/rules")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class RateLimitRuleController {
    
    private final RateLimitRuleRepository ruleRepository;
    private final RateLimiterService rateLimiterService;

    @GetMapping
    public Flux<RateLimitRule> getAllRules() {
        return ruleRepository.findAll();
    }

    @GetMapping("/active")
    public Flux<RateLimitRule> getActiveRules() {
        return ruleRepository.findByActiveTrue();
    }

    @GetMapping("/{id}")
    public Mono<RateLimitRule> getRuleById(@PathVariable UUID id) {
        return ruleRepository.findById(id);
    }

    @PostMapping
    public Mono<RateLimitRule> createRule(@RequestBody RateLimitRule rule) {
        if (rule.getId() == null) {
            rule.setId(UUID.randomUUID());
        }
        return ruleRepository.save(rule)
                .doOnSuccess(saved -> {
                    log.info("Created new rate limit rule: {}", saved);
                    rateLimiterService.refreshRules().subscribe();
                });
    }

    @PutMapping("/{id}")
    public Mono<RateLimitRule> updateRule(@PathVariable UUID id, @RequestBody RateLimitRule rule) {
        return ruleRepository.findById(id)
                .flatMap(existing -> {
                    rule.setId(id);
                    return ruleRepository.save(rule)
                            .doOnSuccess(updated -> {
                                log.info("Updated rate limit rule: {}", updated);
                                rateLimiterService.refreshRules().subscribe();
                            });
                });
    }

    @PatchMapping("/{id}/queue")
    public Mono<RateLimitRule> updateQueueSettings(
            @PathVariable UUID id,
            @RequestBody QueueConfig queueConfig) {
        
        return ruleRepository.findById(id)
                .flatMap(rule -> {
                    rule.setQueueEnabled(queueConfig.queueEnabled);
                    rule.setMaxQueueSize(queueConfig.maxQueueSize);
                    rule.setDelayPerRequestMs(queueConfig.delayPerRequestMs);
                    
                    return ruleRepository.save(rule)
                            .doOnSuccess(updated -> {
                                log.info("Updated queue settings for rule {}: enabled={}, maxSize={}, delayMs={}", 
                                        id, queueConfig.queueEnabled, queueConfig.maxQueueSize, queueConfig.delayPerRequestMs);
                                rateLimiterService.refreshRules().subscribe();
                            });
                });
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteRule(@PathVariable UUID id) {
        return ruleRepository.deleteById(id)
                .doOnSuccess(v -> {
                    log.info("Deleted rate limit rule: {}", id);
                    rateLimiterService.refreshRules().subscribe();
                });
    }

    @PostMapping("/refresh")
    public Mono<Void> refreshRules() {
        log.info("Manually refreshing rate limit rules");
        return rateLimiterService.refreshRules();
    }

    // DTO for queue configuration
    public static class QueueConfig {
        public boolean queueEnabled;
        public int maxQueueSize;
        public int delayPerRequestMs;
    }
}

package com.example.gateway.controller;

import com.example.gateway.model.RateLimitRule;
import com.example.gateway.repository.RateLimitRuleRepository;
import com.example.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
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
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

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
        return r2dbcEntityTemplate.insert(RateLimitRule.class)
                .using(rule)
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

    @PatchMapping("/{id}/body-limit")
    public Mono<RateLimitRule> updateBodyLimitSettings(
            @PathVariable UUID id,
            @RequestBody BodyLimitConfig bodyLimitConfig) {
        
        return ruleRepository.findById(id)
                .flatMap(rule -> {
                    rule.setBodyLimitEnabled(bodyLimitConfig.bodyLimitEnabled);
                    rule.setBodyFieldPath(bodyLimitConfig.bodyFieldPath);
                    rule.setBodyLimitType(bodyLimitConfig.bodyLimitType);
                    
                    return ruleRepository.save(rule)
                            .doOnSuccess(updated -> {
                                log.info("Updated body limit settings for rule {}: enabled={}, fieldPath={}, type={}", 
                                        id, bodyLimitConfig.bodyLimitEnabled, bodyLimitConfig.bodyFieldPath, 
                                        bodyLimitConfig.bodyLimitType);
                                rateLimiterService.refreshRules().subscribe();
                            });
                });
    }

    // DTO for queue configuration
    public static class QueueConfig {
        public boolean queueEnabled;
        public int maxQueueSize;
        public int delayPerRequestMs;
    }

    // DTO for body-based rate limiting configuration
    public static class BodyLimitConfig {
        public boolean bodyLimitEnabled;      // Enable/disable body-based rate limiting
        public String bodyFieldPath;          // JSON path to extract (e.g., "user_id", "api_key", "user.id")
        public String bodyLimitType;          // "replace_ip" (use body field instead of IP) or "combine_with_ip"
    }
}

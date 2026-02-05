package com.example.gateway.service;

import com.example.gateway.model.RateLimitRule;
import com.example.gateway.store.RateLimitRuleStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisBootstrapService {

    private static final UUID DEFAULT_RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final RateLimitRuleStore ruleStore;

    @PostConstruct
    public void seedDefaults() {
        seedDefaultRule().subscribe();
    }

    private Mono<Void> seedDefaultRule() {
        RateLimitRule defaultRule = new RateLimitRule(
                DEFAULT_RULE_ID,
                "/**",
            null,
                100,
                60,
                true,
                0,
                false,
                0,
                100,
                false,
                null,
                ":",
                false,
                null,
                "replace_ip",
                "application/json",
                false,
                null,
                "replace_ip",
                false,
                null,
                "replace_ip",
                null,
                null);

        return ruleStore.findById(DEFAULT_RULE_ID)
                .switchIfEmpty(ruleStore.save(defaultRule)
                        .doOnSuccess(rule -> log.info("Seeded default rate limit rule")))
                .then();
    }
}

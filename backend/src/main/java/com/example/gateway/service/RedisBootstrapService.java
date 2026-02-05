package com.example.gateway.service;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.model.RateLimitRule;
import com.example.gateway.store.RateLimitPolicyStore;
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
    private final RateLimitPolicyStore policyStore;

    @PostConstruct
    public void seedDefaults() {
        seedDefaultRule().subscribe();
        seedDefaultPolicy().subscribe();
    }

    private Mono<Void> seedDefaultRule() {
        RateLimitRule defaultRule = new RateLimitRule(
                DEFAULT_RULE_ID,
                "/**",
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
                "replace_ip");

        return ruleStore.findById(DEFAULT_RULE_ID)
                .switchIfEmpty(ruleStore.save(defaultRule)
                        .doOnSuccess(rule -> log.info("Seeded default rate limit rule")))
                .then();
    }

    private Mono<Void> seedDefaultPolicy() {
        RateLimitPolicy defaultPolicy = new RateLimitPolicy();
        defaultPolicy.setRoutePattern("/**");
        defaultPolicy.setLimitType("GLOBAL");
        defaultPolicy.setReplenishRate(10);
        defaultPolicy.setBurstCapacity(20);
        defaultPolicy.setRequestedTokens(1);

        return policyStore.findAll()
                .hasElements()
                .flatMap(hasElements -> {
                    if (hasElements) {
                        return Mono.empty();
                    }
                    return policyStore.save(defaultPolicy)
                            .doOnSuccess(policy -> log.info("Seeded default rate limit policy"))
                            .then();
                });
    }
}

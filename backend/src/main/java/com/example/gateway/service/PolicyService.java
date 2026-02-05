package com.example.gateway.service;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.repository.RateLimitPolicyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {
    private final RateLimitPolicyRepository repository;

    // Cache all policies to avoid DB hits on every request for matching
    private final Cache<String, List<RateLimitPolicy>> policyCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    private static final String ALL_POLICIES_KEY = "ALL";

    public Flux<RateLimitPolicy> getAllPolicies() {
        List<RateLimitPolicy> cached = policyCache.getIfPresent(ALL_POLICIES_KEY);
        if (cached != null) {
            return Flux.fromIterable(cached);
        }
        return repository.findAll()
                .collectList()
                .doOnNext(policies -> policyCache.put(ALL_POLICIES_KEY, policies))
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<RateLimitPolicy> createPolicy(RateLimitPolicy policy) {
        return repository.save(policy)
                .doOnSuccess(p -> policyCache.invalidate(ALL_POLICIES_KEY));
    }

    public Mono<RateLimitPolicy> updatePolicy(Long id, RateLimitPolicy policy) {
        return repository.findById(id)
                .flatMap(existing -> {
                    existing.setRoutePattern(policy.getRoutePattern());
                    existing.setLimitType(policy.getLimitType());
                    existing.setReplenishRate(policy.getReplenishRate());
                    existing.setBurstCapacity(policy.getBurstCapacity());
                    existing.setRequestedTokens(policy.getRequestedTokens());
                    return repository.save(existing);
                })
                .doOnSuccess(p -> policyCache.invalidate(ALL_POLICIES_KEY));
    }

    public Mono<Void> deletePolicy(Long id) {
        return repository.deleteById(id)
                .doOnSuccess(v -> policyCache.invalidate(ALL_POLICIES_KEY));
    }
}

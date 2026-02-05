package com.example.gateway.repository;

import com.example.gateway.model.RateLimitRule;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface RateLimitRuleRepository extends R2dbcRepository<RateLimitRule, UUID> {
    Flux<RateLimitRule> findByActiveTrue();
}

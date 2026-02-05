package com.example.gateway.repository;

import com.example.gateway.model.RequestCounter;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface RequestCounterRepository extends R2dbcRepository<RequestCounter, UUID> {
    Mono<RequestCounter> findByRuleIdAndClientIp(UUID ruleId, String clientIp);
}

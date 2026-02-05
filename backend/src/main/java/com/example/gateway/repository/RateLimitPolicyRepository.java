package com.example.gateway.repository;

import com.example.gateway.model.RateLimitPolicy;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RateLimitPolicyRepository extends R2dbcRepository<RateLimitPolicy, Long> {
    // Inherits findAll() from R2dbcRepository
}

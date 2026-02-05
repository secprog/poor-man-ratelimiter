package com.example.gateway.repository;

import com.example.gateway.model.SystemConfig;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigRepository extends R2dbcRepository<SystemConfig, String> {
}

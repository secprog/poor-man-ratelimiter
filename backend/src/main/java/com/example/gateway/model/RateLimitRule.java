package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("rate_limit_rules")
public class RateLimitRule {
    @Id
    private UUID id;
    private String pathPattern;
    private int allowedRequests;
    private int windowSeconds;
    private boolean active;
}

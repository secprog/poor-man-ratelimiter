package com.example.gateway.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("rate_limit_policies")
public class RateLimitPolicy {
    @Id
    private Long policyId;
    private String routePattern;
    private String limitType; // IP_BASED, USER_BASED, GLOBAL
    private Integer replenishRate;
    private Integer burstCapacity;
    private Integer requestedTokens;
    private Instant createdAt;
}

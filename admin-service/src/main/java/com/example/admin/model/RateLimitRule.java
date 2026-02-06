package com.example.admin.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {
    private UUID id;
    private String pathPattern;
    private String targetUri;
    private Integer allowedRequests;
    private Integer windowSeconds;
    private Boolean active = true;
    private Integer priority = 0;

    // Queueing configuration
    private Boolean queueEnabled = false;
    private Integer maxQueueSize = 0;
    private Integer delayPerRequestMs = 0;

    // JWT-based rate limiting
    private Boolean jwtEnabled = false;
    private String jwtClaims;
    private String jwtClaimSeparator = ":";

    // Header-based rate limiting
    private Boolean headerLimitEnabled = false;
    private String headerName;
    private String headerLimitType = "replace_ip";

    // Cookie-based rate limiting
    private Boolean cookieLimitEnabled = false;
    private String cookieName;
    private String cookieLimitType = "replace_ip";

    // Body field-based rate limiting
    private Boolean bodyLimitEnabled = false;
    private String bodyFieldPath;
    private String bodyLimitType = "replace_ip";
    private String bodyContentType = "application/json";

    // Route predicates
    private String methods;
    private String hosts;
}

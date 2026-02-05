package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRule {
    @Id
    private UUID id;
    private String pathPattern;
    private String targetUri;
    private int allowedRequests;
    private int windowSeconds;
    private boolean active;
    private int priority;  // Order of rule evaluation (lower = higher priority)
    
    // Leaky bucket / queueing configuration
    private boolean queueEnabled;
    private int maxQueueSize;
    private int delayPerRequestMs;
    
    // JWT-based rate limiting configuration
    private boolean jwtEnabled;
    private String jwtClaims;  // JSON array of claim names (e.g., ["sub", "tenant_id"])
    private String jwtClaimSeparator;  // Separator for concatenating claims (e.g., ":")
    
    // Body-based rate limiting configuration
    private boolean bodyLimitEnabled;
    private String bodyFieldPath;  // JSONPath or simple field name (e.g., "user_id", "api_key", "user.id")
    private String bodyLimitType;  // "replace_ip" (use body value instead of IP), "combine_with_ip" (combine IP + field)
    private String bodyContentType;  // Expected content type (e.g., "application/json", "application/x-www-form-urlencoded", "application/xml", "multipart/form-data")
    
    // Header-based rate limiting configuration
    private boolean headerLimitEnabled;
    private String headerName;  // Header name to extract from (e.g., "X-API-Key", "X-User-Id")
    private String headerLimitType;  // "replace_ip" or "combine_with_ip"
    
    // Cookie-based rate limiting configuration
    private boolean cookieLimitEnabled;
    private String cookieName;  // Cookie name to extract from (e.g., "session_id", "user_token")
    private String cookieLimitType;  // "replace_ip" or "combine_with_ip"
}

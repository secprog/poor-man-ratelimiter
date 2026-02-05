package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * CRITICAL SECURITY FILTER: Port-based isolation of admin endpoints.
 * 
 * Admin APIs (/poormansRateLimit/api/admin/**) must ONLY be accessible on port 9090.
 * This filter ensures they are completely invisible (404) on the main gateway port 8080.
 * 
 * Security model:
 * - Port 8080: Main gateway (public) - BLOCKS ALL admin routes
 * - Port 9090: Admin server (private) - ALLOWS admin routes + validates localhost
 * 
 * Runs with order HIGHEST_PRECEDENCE (before all other filters) to fail-fast.
 * 
 * Note: This filter rejects requests with 404 to make routes truly non-existent
 * on port 8080, preventing any information leakage about admin API structure.
 */
@Component
@Slf4j
public class ApiPortFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String remoteAddress = exchange.getRequest().getRemoteAddress() != null 
            ? exchange.getRequest().getRemoteAddress().toString() 
            : "unknown";
        
        // SECURITY: Block ALL admin routes on port 8080
        // Admin API MUST only be accessible on port 9090
        if (path.startsWith("/poormansRateLimit/api/admin/")) {
            log.warn("SECURITY: Admin API access attempt on gateway port 8080 - blocking");
            log.warn("  Path: {}", path);
            log.warn("  Source: {}", remoteAddress);
            log.warn("  Hint: Admin APIs are only available on port 9090 (localhost only)");
            
            // Return 404 to make admin routes appear non-existent on port 8080
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        
        // Allow all other requests to proceed through the gateway
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run FIRST (highest precedence) to fail-fast on admin route access attempts
        // This ensures no other filters process admin requests on port 8080
        return HIGHEST_PRECEDENCE;
    }
}


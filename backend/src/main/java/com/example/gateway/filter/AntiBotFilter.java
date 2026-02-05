package com.example.gateway.filter;

import com.example.gateway.service.AnalyticsService;
import com.example.gateway.service.ConfigurationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Anti-bot protection filter implementing multiple defense mechanisms:
 * 
 * 1. Honeypot fields - Hidden inputs that bots fill but humans don't
 * 2. Time-to-submit checks - Detect bots that submit too fast
 * 3. One-time form tokens - Prevent replay attacks
 * 4. Idempotency keys - Prevent duplicate submissions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AntiBotFilter implements GlobalFilter, Ordered {

    private final ConfigurationService configService;
    private final AnalyticsService analyticsService;

    // ... cache definitions ...

    // ... filter method ...

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String reason) {
        analyticsService.incrementBlocked();
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
        return exchange.getResponse().setComplete();
    }

    private final Cache<String, Long> validTokens = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10)) // Tokens expire after 10 minutes
            .maximumSize(100_000)
            .build();

    // Cache for used tokens (to prevent reuse)
    private final Cache<String, Boolean> usedTokens = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15)) // Remember used tokens for 15 minutes
            .maximumSize(100_000)
            .build();

    // Cache for idempotency keys (to prevent duplicate submissions)
    private final Cache<String, Boolean> idempotencyKeys = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1)) // Idempotency keys valid for 1 hour
            .maximumSize(100_000)
            .build();

    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        boolean enabled = configService.getBoolean("antibot-enabled", true);
        if (!enabled) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();

        // Only apply to POST/PUT/PATCH requests (form submissions)
        if (!method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH")) {
            return chain.filter(exchange);
        }

        // Check for anti-bot headers
        String formToken = exchange.getRequest().getHeaders().getFirst("X-Form-Token");
        String formLoadTime = exchange.getRequest().getHeaders().getFirst("X-Form-Load-Time");
        String honeypotValue = exchange.getRequest().getHeaders().getFirst("X-Honeypot");
        String idempotencyKey = exchange.getRequest().getHeaders().getFirst("X-Idempotency-Key");

        // 1. Honeypot check - if filled, it's a bot
        if (honeypotValue != null && !honeypotValue.isEmpty()) {
            log.warn("Honeypot triggered from IP: {}", getClientIp(exchange));
            return rejectRequest(exchange, "Bot detected");
        }

        // 2. Time-to-submit check
        if (formLoadTime != null) {
            try {
                long loadTime = Long.parseLong(formLoadTime);
                long submitTime = System.currentTimeMillis();
                long elapsed = submitTime - loadTime;

                long minSubmitTimeMs = configService.getLong("antibot-min-submit-time", 2000);

                if (elapsed < minSubmitTimeMs) {
                    log.warn("Form submitted too fast ({} ms) from IP: {}", elapsed, getClientIp(exchange));
                    return rejectRequest(exchange, "Form submitted too quickly");
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid form load time from IP: {}", getClientIp(exchange));
            }
        }

        // 3. One-time token validation
        if (formToken != null) {
            // Check if token was already used
            if (usedTokens.getIfPresent(formToken) != null) {
                log.warn("Reused form token from IP: {}", getClientIp(exchange));
                return rejectRequest(exchange, "Form token already used");
            }

            // Check if token is valid (was issued by us)
            Long tokenIssueTime = validTokens.getIfPresent(formToken);
            if (tokenIssueTime == null) {
                log.warn("Invalid form token from IP: {}", getClientIp(exchange));
                return rejectRequest(exchange, "Invalid form token");
            }

            // Mark token as used
            usedTokens.put(formToken, true);
            validTokens.invalidate(formToken);
        }

        // 4. Idempotency key check
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            if (idempotencyKeys.getIfPresent(idempotencyKey) != null) {
                log.info("Duplicate request blocked with idempotency key: {}", idempotencyKey);
                // Return 409 Conflict for duplicate submission
                exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
                exchange.getResponse().getHeaders().add("X-Duplicate-Request", "true");
                return exchange.getResponse().setComplete();
            }
            // Record this idempotency key
            idempotencyKeys.put(idempotencyKey, true);
        }

        return chain.filter(exchange);
    }

    /**
     * Generate a new one-time form token
     */
    public String generateFormToken() {
        String token = UUID.randomUUID().toString();
        validTokens.put(token, System.currentTimeMillis());
        return token;
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String reason) {
        configService.incrementBlocked(); // Using configService? No, need AnalyticsService.
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("X-Rejection-Reason", reason);
        return exchange.getResponse().setComplete();
    }

    private String getClientIp(ServerWebExchange exchange) {
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }
}

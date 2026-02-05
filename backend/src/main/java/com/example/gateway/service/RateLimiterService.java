package com.example.gateway.service;

import com.example.gateway.dto.RateLimitResult;
import com.example.gateway.model.RateLimitRule;
import com.example.gateway.model.RequestCounter;
import com.example.gateway.model.TrafficLog;
import com.example.gateway.repository.RateLimitRuleRepository;
import com.example.gateway.repository.RequestCounterRepository;
import com.example.gateway.repository.TrafficLogRepository;
import com.example.gateway.util.BodyFieldExtractor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RateLimitRuleRepository ruleRepository;
    private final RequestCounterRepository counterRepository;
    private final TrafficLogRepository logRepository;
    private final DatabaseClient databaseClient;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<RateLimitRule> activeRules = new CopyOnWriteArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // Track queue depth per rule+identifier for leaky bucket delays
    private final Map<String, AtomicInteger> queueDepths = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadRules() {
        refreshRules().subscribe();
        // Start background task to clean up stale queue entries
        startQueueCleanupTask();
    }
    
    private void startQueueCleanupTask() {
        // Clean up queue depths every 60 seconds
        Mono.delay(Duration.ofSeconds(60))
                .repeat()
                .subscribe(tick -> {
                    queueDepths.entrySet().removeIf(entry -> entry.getValue().get() <= 0);
                    log.debug("Queue cleanup: {} active queues", queueDepths.size());
                });
    }

    public Mono<Void> refreshRules() {
        return ruleRepository.findByActiveTrue()
                .collectList()
                .doOnNext(rules -> {
                    activeRules.clear();
                    // Sort by priority (lower number = higher priority)
                    rules.sort((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()));
                    activeRules.addAll(rules);
                    log.info("Loaded {} active rate limit rules (sorted by priority)", rules.size());
                })
                .then();
    }

    /**
     * Check if a request is allowed based on rate limiting rules.
     * Supports both IP-based and JWT-based rate limiting.
     * This is a convenience method that doesn't support header/cookie extraction.
     *
     * @param path Request path
     * @param clientIp Client IP address
     * @param authHeader Authorization header (may be null)
     * @return RateLimitResult indicating if request is allowed, and any delay
     */
    public Mono<RateLimitResult> isAllowed(String path, String clientIp, String authHeader) {
        // Use a minimal exchange object - this won't support header/cookie extraction
        return isAllowed(null, path, clientIp, authHeader, (byte[]) null);
    }

    /**
     * Check if a request is allowed based on rate limiting rules.
     * Supports IP-based, JWT-based, body-based, header-based, and cookie-based rate limiting.
     *
     * @param exchange ServerWebExchange for accessing headers and cookies
     * @param path Request path
     * @param clientIp Client IP address
     * @param authHeader Authorization header (may be null)
     * @param bodyBytes Raw request body bytes (may be null)
     * @return RateLimitResult indicating if request is allowed, and any delay
     */
    public Mono<RateLimitResult> isAllowed(ServerWebExchange exchange, String path, String clientIp, String authHeader, byte[] bodyBytes) {
        // Find matching rule (first match wins)
        RateLimitRule matchedRule = activeRules.stream()
                .filter(rule -> pathMatcher.match(rule.getPathPattern(), path))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            return logTraffic(path, clientIp, 200, true)
                    .thenReturn(new RateLimitResult(true, 0, false));
        }

        // Extract body field value if body-based limiting is enabled
        String bodyFieldValue = null;
        if (matchedRule.isBodyLimitEnabled() && matchedRule.getBodyFieldPath() != null && bodyBytes != null) {
            bodyFieldValue = extractBodyField(exchange, bodyBytes, matchedRule.getBodyFieldPath(), 
                    matchedRule.getBodyContentType());
        }

        // Determine the identifier to use for rate limiting
        String identifier = determineIdentifier(exchange, matchedRule, clientIp, authHeader, bodyFieldValue);
        
        return checkLimit(matchedRule, identifier)
                .flatMap(result -> {
                    int status = result.isAllowed() ? 200 : 429;
                    return logTraffic(path, clientIp, status, result.isAllowed())
                            .thenReturn(result);
                });
    }

    /**
     * Determine the identifier to use for rate limiting based on rule configuration.
     * Priority: Header > Cookie > Body field > JWT claims > IP address
     * If header, cookie, JWT, or body limiting is enabled and configured, try to extract their values.
     * Fall back to IP address if extraction fails or is not configured.
     *
     * @param exchange ServerWebExchange for accessing headers and cookies
     * @param rule Rate limit rule
     * @param clientIp Client IP address (fallback)
     * @param authHeader Authorization header (may be null)
     * @param bodyFieldValue Extracted body field value (may be null)
     * @return Identifier string for rate limiting (header, cookie, body value, JWT claims, or IP)
     */
    private String determineIdentifier(ServerWebExchange exchange, RateLimitRule rule, String clientIp, String authHeader, String bodyFieldValue) {
        // Check header-based limiting first (highest priority)
        if (exchange != null && rule.isHeaderLimitEnabled() && rule.getHeaderName() != null && !rule.getHeaderName().isBlank()) {
            String headerValue = exchange.getRequest().getHeaders().getFirst(rule.getHeaderName());
            if (headerValue != null && !headerValue.isBlank()) {
                String limitType = rule.getHeaderLimitType() != null ? rule.getHeaderLimitType() : "replace_ip";
                
                if ("combine_with_ip".equalsIgnoreCase(limitType)) {
                    String identifier = clientIp + ":" + headerValue;
                    log.debug("Using header+IP combined identifier for rate limiting: {} (header: {})", 
                            identifier, rule.getHeaderName());
                    return identifier;
                } else {
                    log.debug("Using header-based identifier for rate limiting: {} (header: {})", 
                            headerValue, rule.getHeaderName());
                    return headerValue;
                }
            } else {
                log.debug("Header '{}' not found or empty, falling back to next identifier option",
                        rule.getHeaderName());
            }
        }
        
        // Check cookie-based limiting second
        if (exchange != null && rule.isCookieLimitEnabled() && rule.getCookieName() != null && !rule.getCookieName().isBlank()) {
            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(rule.getCookieName());
            if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                String cookieValue = cookie.getValue();
                String limitType = rule.getCookieLimitType() != null ? rule.getCookieLimitType() : "replace_ip";
                
                if ("combine_with_ip".equalsIgnoreCase(limitType)) {
                    String identifier = clientIp + ":" + cookieValue;
                    log.debug("Using cookie+IP combined identifier for rate limiting: {} (cookie: {})", 
                            identifier, rule.getCookieName());
                    return identifier;
                } else {
                    log.debug("Using cookie-based identifier for rate limiting: {} (cookie: {})", 
                            cookieValue, rule.getCookieName());
                    return cookieValue;
                }
            } else {
                log.debug("Cookie '{}' not found or empty, falling back to next identifier option",
                        rule.getCookieName());
            }
        }
        
        // Check body-based limiting third
        if (rule.isBodyLimitEnabled() && rule.getBodyFieldPath() != null && !rule.getBodyFieldPath().isBlank()) {
            if (bodyFieldValue != null && !bodyFieldValue.isBlank()) {
                String limitType = rule.getBodyLimitType() != null ? rule.getBodyLimitType() : "replace_ip";
                
                if ("combine_with_ip".equalsIgnoreCase(limitType)) {
                    // Combine IP and body field value
                    String identifier = clientIp + ":" + bodyFieldValue;
                    log.debug("Using body+IP combined identifier for rate limiting: {} (field: {}, content-type: {})", 
                            identifier, rule.getBodyFieldPath(), rule.getBodyContentType());
                    return identifier;
                } else {
                    // replace_ip: use body field value instead of IP
                    log.debug("Using body-based identifier for rate limiting: {} (field: {}, content-type: {})", 
                            bodyFieldValue, rule.getBodyFieldPath(), rule.getBodyContentType());
                    return bodyFieldValue;
                }
            } else {
                log.debug("Body field '{}' not found or empty, falling back to next identifier option",
                        rule.getBodyFieldPath());
            }
        }
        
        // Check JWT-based limiting fourth
        if (rule.isJwtEnabled()) {
            // JWT is enabled, try to extract claims
            if (authHeader == null || authHeader.isBlank()) {
                log.debug("JWT enabled but no Authorization header present, falling back to IP: {}", clientIp);
                return clientIp;
            }

            String jwtClaimsJson = rule.getJwtClaims();
            if (jwtClaimsJson == null || jwtClaimsJson.isBlank()) {
                log.warn("JWT enabled but no claims configured for rule {}, falling back to IP: {}",
                        rule.getPathPattern(), clientIp);
                return clientIp;
            }

            try {
                // Parse JWT claims array from JSON
                List<String> claimNames = objectMapper.readValue(jwtClaimsJson, new TypeReference<List<String>>() {});
                
                if (claimNames.isEmpty()) {
                    log.warn("JWT claims array is empty for rule {}, falling back to IP: {}",
                            rule.getPathPattern(), clientIp);
                    return clientIp;
                }

                // Extract claims from JWT
                String separator = rule.getJwtClaimSeparator() != null ? rule.getJwtClaimSeparator() : ":";
                String jwtIdentifier = jwtService.extractClaims(authHeader, claimNames, separator);

                if (jwtIdentifier == null) {
                    log.debug("Failed to extract JWT claims for rule {}, falling back to IP: {}",
                            rule.getPathPattern(), clientIp);
                    return clientIp;
                }

                log.debug("Using JWT-based identifier for rate limiting: {} (claims: {})",
                        jwtIdentifier, claimNames);
                return jwtIdentifier;

            } catch (Exception e) {
                log.warn("Failed to parse JWT claims configuration for rule {}: {}, falling back to IP: {}",
                        rule.getPathPattern(), e.getMessage(), clientIp);
                return clientIp;
            }
        }
        
        // Default to IP-based limiting
        return clientIp;
    }

    private Mono<RateLimitResult> checkLimit(RateLimitRule rule, String identifier) {
        return counterRepository.findByRuleIdAndClientIp(rule.getId(), identifier)
                .defaultIfEmpty(new RequestCounter(rule.getId(), identifier, 0, LocalDateTime.now()))
                .flatMap(counter -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime windowEnd = counter.getWindowStart().plusSeconds(rule.getWindowSeconds());

                    if (now.isAfter(windowEnd)) {
                        // Reset and allow
                        return updateCounter(rule.getId(), identifier, 1, now)
                                .thenReturn(new RateLimitResult(true, 0, false));
                    } else {
                        if (counter.getRequestCount() < rule.getAllowedRequests()) {
                            // Increment and allow
                            return updateCounter(rule.getId(), identifier, counter.getRequestCount() + 1, counter.getWindowStart())
                                    .thenReturn(new RateLimitResult(true, 0, false));
                        } else {
                            // Rate limit exceeded
                            if (rule.isQueueEnabled()) {
                                return handleQueue(rule, identifier);
                            } else {
                                return Mono.just(new RateLimitResult(false, 0, false));
                            }
                        }
                    }
                });
    }
    
    private Mono<RateLimitResult> handleQueue(RateLimitRule rule, String identifier) {
        String queueKey = rule.getId() + ":" + identifier;
        AtomicInteger queueDepth = queueDepths.computeIfAbsent(queueKey, k -> new AtomicInteger(0));
        
        // Atomically check and increment queue depth
        int position;
        while (true) {
            int currentDepth = queueDepth.get();
            
            // Check if queue is full
            if (currentDepth >= rule.getMaxQueueSize()) {
                log.debug("Queue full for rule {} and identifier {}: depth={}, max={}", 
                        rule.getPathPattern(), identifier, currentDepth, rule.getMaxQueueSize());
                return Mono.just(new RateLimitResult(false, 0, false));
            }
            
            // Try to atomically increment
            if (queueDepth.compareAndSet(currentDepth, currentDepth + 1)) {
                position = currentDepth + 1;  // This is our position in the queue
                break;
            }
            // If CAS failed, loop and try again
        }
        
        // Calculate delay based on position
        long delayMs = (long) position * rule.getDelayPerRequestMs();
        
        log.debug("Request queued for rule {} and identifier {}: position={}, delay={}ms", 
                rule.getPathPattern(), identifier, position, delayMs);
        
        // Schedule decrement after the delay to allow new requests
        Mono.delay(Duration.ofMillis(delayMs))
                .doOnNext(tick -> {
                    queueDepth.decrementAndGet();
                    log.trace("Queue depth decremented for {}: now {}", queueKey, queueDepth.get());
                })
                .subscribe();
        
        return Mono.just(new RateLimitResult(true, delayMs, true));
    }

    private Mono<Void> updateCounter(UUID ruleId, String identifier, int newCount, LocalDateTime windowStart) {
        // Use raw SQL upsert instead of repository.save() to avoid R2DBC's
        // confusing entity detection logic
        return databaseClient.sql(
                "INSERT INTO request_counters (rule_id, client_ip, request_count, window_start) " +
                "VALUES (:rule_id, :client_ip, :request_count, :window_start) " +
                "ON CONFLICT (rule_id, client_ip) DO UPDATE SET request_count = :request_count, window_start = :window_start")
                .bind("rule_id", ruleId)
                .bind("client_ip", identifier)  // Using 'identifier' which can be IP or JWT claims
                .bind("request_count", newCount)
                .bind("window_start", windowStart)
                .then()
                .onErrorResume(e -> {
                    // Log but don't fail the request
                    log.warn("Failed to update counter: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> logTraffic(String path, String ip, int status, boolean allowed) {
        // Use raw SQL INSERT instead of repository.save() to avoid R2DBC's
        // confusing entity detection logic that tries to UPDATE instead of INSERT
        return databaseClient.sql(
                "INSERT INTO traffic_logs (id, timestamp, path, client_ip, status_code, allowed) " +
                "VALUES (:id, :timestamp, :path, :client_ip, :status_code, :allowed)")
                .bind("id", UUID.randomUUID())
                .bind("timestamp", LocalDateTime.now())
                .bind("path", path)
                .bind("client_ip", ip)
                .bind("status_code", status)
                .bind("allowed", allowed)
                .then()
                .onErrorResume(e -> {
                    // Log but don't fail the request
                    log.warn("Failed to log traffic: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Extract a field value from request body based on content type.
     *
     * @param exchange ServerWebExchange for accessing headers (content-type)
     * @param bodyBytes Raw request body bytes
     * @param fieldPath Field path (dot-notation for JSON, field name for forms/multipart, XPath for XML)
     * @param expectedContentType Expected content type from rule configuration
     * @return Extracted field value, or null if not found or parsing fails
     */
    private String extractBodyField(ServerWebExchange exchange, byte[] bodyBytes, String fieldPath, String expectedContentType) {
        // Get actual content type from request headers
        String actualContentType = null;
        if (exchange != null && exchange.getRequest() != null) {
            actualContentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        }
        
        // Use expected content type from rule if available, otherwise use actual from request
        String contentTypeToUse = (expectedContentType != null && !expectedContentType.isBlank()) 
                ? expectedContentType 
                : actualContentType;
        
        return BodyFieldExtractor.extractField(bodyBytes, fieldPath, contentTypeToUse);
    }
}

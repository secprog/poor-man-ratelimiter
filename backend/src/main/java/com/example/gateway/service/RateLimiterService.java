package com.example.gateway.service;

import com.example.gateway.dto.RateLimitResult;
import com.example.gateway.model.RateLimitRule;
import com.example.gateway.model.RequestCounter;
import com.example.gateway.model.TrafficLog;
import com.example.gateway.repository.RateLimitRuleRepository;
import com.example.gateway.repository.RequestCounterRepository;
import com.example.gateway.repository.TrafficLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
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

    private final List<RateLimitRule> activeRules = new CopyOnWriteArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // Track queue depth per rule+IP for leaky bucket delays
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
                    activeRules.addAll(rules);
                    log.info("Loaded {} active rate limit rules", rules.size());
                })
                .then();
    }

    public Mono<RateLimitResult> isAllowed(String path, String clientIp) {
        // Find matching rule (first match wins or most specific? strict order?)
        // For simplicity: first match.
        RateLimitRule matchedRule = activeRules.stream()
                .filter(rule -> pathMatcher.match(rule.getPathPattern(), path))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            return logTraffic(path, clientIp, 200, true)
                    .thenReturn(new RateLimitResult(true, 0, false));
        }

        return checkLimit(matchedRule, clientIp)
                .flatMap(result -> {
                    int status = result.isAllowed() ? 200 : 429;
                    return logTraffic(path, clientIp, status, result.isAllowed())
                            .thenReturn(result);
                });
    }

    private Mono<RateLimitResult> checkLimit(RateLimitRule rule, String clientIp) {
        return counterRepository.findByRuleIdAndClientIp(rule.getId(), clientIp)
                .defaultIfEmpty(new RequestCounter(rule.getId(), clientIp, 0, LocalDateTime.now()))
                .flatMap(counter -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime windowEnd = counter.getWindowStart().plusSeconds(rule.getWindowSeconds());

                    if (now.isAfter(windowEnd)) {
                        // Reset and allow
                        return updateCounter(rule.getId(), clientIp, 1, now)
                                .thenReturn(new RateLimitResult(true, 0, false));
                    } else {
                        if (counter.getRequestCount() < rule.getAllowedRequests()) {
                            // Increment and allow
                            return updateCounter(rule.getId(), clientIp, counter.getRequestCount() + 1, counter.getWindowStart())
                                    .thenReturn(new RateLimitResult(true, 0, false));
                        } else {
                            // Rate limit exceeded
                            if (rule.isQueueEnabled()) {
                                return handleQueue(rule, clientIp);
                            } else {
                                return Mono.just(new RateLimitResult(false, 0, false));
                            }
                        }
                    }
                });
    }
    
    private Mono<RateLimitResult> handleQueue(RateLimitRule rule, String clientIp) {
        String queueKey = rule.getId() + ":" + clientIp;
        AtomicInteger queueDepth = queueDepths.computeIfAbsent(queueKey, k -> new AtomicInteger(0));
        
        // Atomically check and increment queue depth
        int position;
        while (true) {
            int currentDepth = queueDepth.get();
            
            // Check if queue is full
            if (currentDepth >= rule.getMaxQueueSize()) {
                log.debug("Queue full for rule {} and IP {}: depth={}, max={}", 
                        rule.getPathPattern(), clientIp, currentDepth, rule.getMaxQueueSize());
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
        
        log.debug("Request queued for rule {} and IP {}: position={}, delay={}ms", 
                rule.getPathPattern(), clientIp, position, delayMs);
        
        // Schedule decrement after the delay to allow new requests
        Mono.delay(Duration.ofMillis(delayMs))
                .doOnNext(tick -> {
                    queueDepth.decrementAndGet();
                    log.trace("Queue depth decremented for {}: now {}", queueKey, queueDepth.get());
                })
                .subscribe();
        
        return Mono.just(new RateLimitResult(true, delayMs, true));
    }

    private Mono<Void> updateCounter(UUID ruleId, String clientIp, int newCount, LocalDateTime windowStart) {
        // Use raw SQL upsert instead of repository.save() to avoid R2DBC's
        // confusing entity detection logic
        return databaseClient.sql(
                "INSERT INTO request_counters (rule_id, client_ip, request_count, window_start) " +
                "VALUES (:rule_id, :client_ip, :request_count, :window_start) " +
                "ON CONFLICT (rule_id, client_ip) DO UPDATE SET request_count = :request_count, window_start = :window_start")
                .bind("rule_id", ruleId)
                .bind("client_ip", clientIp)
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
}

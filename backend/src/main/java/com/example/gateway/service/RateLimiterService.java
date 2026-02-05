package com.example.gateway.service;

import com.example.gateway.model.RateLimitRule;
import com.example.gateway.model.RequestCounter;
import com.example.gateway.model.TrafficLog;
import com.example.gateway.repository.RateLimitRuleRepository;
import com.example.gateway.repository.RequestCounterRepository;
import com.example.gateway.repository.TrafficLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RateLimitRuleRepository ruleRepository;
    private final RequestCounterRepository counterRepository;
    private final TrafficLogRepository logRepository;

    private final List<RateLimitRule> activeRules = new CopyOnWriteArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    public void loadRules() {
        refreshRules().subscribe();
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

    public Mono<Boolean> isAllowed(String path, String clientIp) {
        // Find matching rule (first match wins or most specific? strict order?)
        // For simplicity: first match.
        RateLimitRule matchedRule = activeRules.stream()
                .filter(rule -> pathMatcher.match(rule.getPathPattern(), path))
                .findFirst()
                .orElse(null);

        if (matchedRule == null) {
            return logTraffic(path, clientIp, 200, true).thenReturn(true);
        }

        return checkLimit(matchedRule, clientIp)
                .flatMap(allowed -> {
                    int status = allowed ? 200 : 429;
                    return logTraffic(path, clientIp, status, allowed).thenReturn(allowed);
                });
    }

    private Mono<Boolean> checkLimit(RateLimitRule rule, String clientIp) {
        return counterRepository.findByRuleIdAndClientIp(rule.getId(), clientIp)
                .defaultIfEmpty(new RequestCounter(rule.getId(), clientIp, 0, LocalDateTime.now()))
                .flatMap(counter -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime windowEnd = counter.getWindowStart().plusSeconds(rule.getWindowSeconds());

                    if (now.isAfter(windowEnd)) {
                        // Reset
                        counter.setWindowStart(now);
                        counter.setRequestCount(1);
                        return counterRepository.save(counter).thenReturn(true);
                    } else {
                        if (counter.getRequestCount() < rule.getAllowedRequests()) {
                            counter.setRequestCount(counter.getRequestCount() + 1);
                            return counterRepository.save(counter).thenReturn(true);
                        } else {
                            return Mono.just(false);
                        }
                    }
                });
    }

    private Mono<Void> logTraffic(String path, String ip, int status, boolean allowed) {
        // Fire and forget logging to avoid latency impact?
        // For robust data, we should await, but `subscribe()` is better for
        // non-blocking log.
        // We'll return Mono<Void> to allow caller to decide, but we won't block the
        // decision on the log save if possible.
        // Ideally: use a separate scheduler.

        TrafficLog entry = new TrafficLog(null, LocalDateTime.now(), path, ip, status, allowed);
        return logRepository.save(entry).then();
    }
}

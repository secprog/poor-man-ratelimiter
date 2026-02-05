package com.example.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final DatabaseClient databaseClient;

    // In-memory counters for buffering
    private final AtomicLong pendingAllowed = new AtomicLong(0);
    private final AtomicLong pendingBlocked = new AtomicLong(0);

    public void incrementAllowed() {
        pendingAllowed.incrementAndGet();
    }

    public void incrementBlocked() {
        pendingBlocked.incrementAndGet();
    }

    // Flush to DB every 5 seconds
    @Scheduled(fixedRate = 5000)
    public void flushStats() {
        long allowed = pendingAllowed.getAndSet(0);
        long blocked = pendingBlocked.getAndSet(0);

        if (allowed == 0 && blocked == 0)
            return;

        // Round to nearest minute for aggregation
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        databaseClient.sql(
                "INSERT INTO request_stats (time_window, allowed_count, blocked_count) " +
                        "VALUES (:time, :allowed, :blocked) " +
                        "ON CONFLICT (time_window) DO UPDATE SET " +
                        "allowed_count = request_stats.allowed_count + :allowed, " +
                        "blocked_count = request_stats.blocked_count + :blocked")
                .bind("time", now)
                .bind("allowed", allowed)
                .bind("blocked", blocked)
                .then()
                .subscribe(
                        null,
                        error -> log.error("Failed to flush analytics stats", error));
    }

    public Mono<StatsSummary> getSummary() {
        // Simple summary: All time or last 24h. Let's do all time for now as table
        // grows.
        // Better: Last 24 hours.
        return databaseClient.sql(
                "SELECT SUM(allowed_count) as total_allowed, SUM(blocked_count) as total_blocked " +
                        "FROM request_stats")
                .map((row, meta) -> new StatsSummary(
                        row.get("total_allowed", Long.class) != null ? row.get("total_allowed", Long.class) : 0L,
                        row.get("total_blocked", Long.class) != null ? row.get("total_blocked", Long.class) : 0L))
                .one()
                .defaultIfEmpty(new StatsSummary(0L, 0L));
    }

    public record StatsSummary(Long allowed, Long blocked) {
    }
}

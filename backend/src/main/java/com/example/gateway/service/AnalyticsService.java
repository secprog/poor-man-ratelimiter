package com.example.gateway.service;

import com.example.gateway.dto.AnalyticsUpdate;
import com.example.gateway.store.RateLimitRuleStore;
import com.example.gateway.store.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveZSetOperations;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RateLimitRuleStore ruleStore;
    private final ConfigurationService configService;

    // In-memory counters for buffering
    private final AtomicLong pendingAllowed = new AtomicLong(0);
    private final AtomicLong pendingBlocked = new AtomicLong(0);

    private static final String ALLOWED_FIELD = "allowed";
    private static final String BLOCKED_FIELD = "blocked";

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    private ReactiveZSetOperations<String, String> zsetOps() {
        return redisTemplate.opsForZSet();
    }

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
        long minuteBucket = now.getEpochSecond() / 60;
        String statsKey = RedisKeys.requestStatsKey(minuteBucket);

        Duration retention = getRetentionDuration();

        Mono.when(
                hashOps().increment(statsKey, ALLOWED_FIELD, allowed),
                hashOps().increment(statsKey, BLOCKED_FIELD, blocked),
                zsetOps().add(RedisKeys.REQUEST_STATS_INDEX, String.valueOf(minuteBucket), minuteBucket),
            redisTemplate.expire(statsKey, retention),
            pruneOldStats(minuteBucket, retention))
            .subscribe(
                null,
                error -> log.error("Failed to flush analytics stats", error));
    }
    
    // Note: Real-time broadcasting moved to admin-service WebSocket handler

        public Mono<AnalyticsUpdate> getCurrentUpdate() {
        return Mono.zip(getSummary(), ruleStore.findByActiveTrue().count())
            .map(tuple -> new AnalyticsUpdate(
                tuple.getT1().allowed(),
                tuple.getT1().blocked(),
                tuple.getT2(),
                System.currentTimeMillis()));
        }

    public Mono<StatsSummary> getSummary() {
        // Last 24 hours to match the time series charts
        Instant startTime = Instant.now().minus(24, ChronoUnit.HOURS);

        return getMinuteBuckets(startTime)
            .flatMapMany(Flux::fromIterable)
            .flatMap(this::loadBucketTotals)
            .reduce(new StatsSummary(0L, 0L), (acc, next) ->
                new StatsSummary(acc.allowed + next.allowed, acc.blocked + next.blocked));
    }

    public Mono<java.util.List<TimeSeriesDataPoint>> getTimeSeries(int hours) {
        Instant startTime = Instant.now().minus(hours, ChronoUnit.HOURS);

        return getMinuteBuckets(startTime)
                .flatMapMany(Flux::fromIterable)
                .flatMap(this::loadBucketPoint)
                .collectList();
    }

    private Mono<List<String>> getMinuteBuckets(Instant startTime) {
        double start = startTime.getEpochSecond() / 60.0;
        return zsetOps()
            .rangeByScore(RedisKeys.REQUEST_STATS_INDEX, Range.closed(start, Double.POSITIVE_INFINITY), Limit.unlimited())
            .collectList();
    }

    private Mono<StatsSummary> loadBucketTotals(String minuteBucket) {
        return hashOps()
                .multiGet(RedisKeys.requestStatsKey(Long.parseLong(minuteBucket)), List.of(ALLOWED_FIELD, BLOCKED_FIELD))
                .map(values -> new StatsSummary(parseLong(values, 0), parseLong(values, 1)));
    }

    private Mono<TimeSeriesDataPoint> loadBucketPoint(String minuteBucket) {
        long minute = Long.parseLong(minuteBucket);
        Instant timestamp = Instant.ofEpochSecond(minute * 60);
        return hashOps()
                .multiGet(RedisKeys.requestStatsKey(minute), List.of(ALLOWED_FIELD, BLOCKED_FIELD))
                .map(values -> new TimeSeriesDataPoint(timestamp, parseLong(values, 0), parseLong(values, 1)));
    }

    private long parseLong(List<String> values, int index) {
        if (values == null || values.size() <= index) {
            return 0L;
        }
        String value = values.get(index);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private Mono<Long> pruneOldStats(long currentMinute, Duration retention) {
        long retentionMinutes = retention.toMinutes();
        long minMinute = currentMinute - retentionMinutes;
        if (minMinute <= 0) {
            return Mono.just(0L);
        }
        return zsetOps().removeRangeByScore(
            RedisKeys.REQUEST_STATS_INDEX,
            Range.closed(0.0, (double) (minMinute - 1)));
    }

    private Duration getRetentionDuration() {
        long days = configService.getLong("analytics-retention-days", 7);
        long clampedDays = Math.max(1, Math.min(days, 90));
        return Duration.ofDays(clampedDays);
    }

    public record StatsSummary(Long allowed, Long blocked) {
    }
    
    public record TimeSeriesDataPoint(Instant timestamp, Long allowed, Long blocked) {
    }
}

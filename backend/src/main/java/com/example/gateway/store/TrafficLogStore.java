package com.example.gateway.store;

import com.example.gateway.model.TrafficLog;
import com.example.gateway.service.ConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficLogStore {

    private static final long DEFAULT_MAX_LOG_ENTRIES = 10000;
    private static final long MIN_LOG_ENTRIES = 1000;
    private static final long MAX_LOG_ENTRIES = 100000;
    private static final long DEFAULT_RETENTION_HOURS = 24;
    private static final long MIN_RETENTION_HOURS = 1;
    private static final long MAX_RETENTION_HOURS = 168;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConfigurationService configService;

    private ReactiveListOperations<String, String> listOps() {
        return redisTemplate.opsForList();
    }

    public Mono<Void> append(TrafficLog logEntry) {
        long maxEntries = clamp(
            configService.getLong("traffic-logs-max-entries", DEFAULT_MAX_LOG_ENTRIES),
            MIN_LOG_ENTRIES,
            MAX_LOG_ENTRIES);
        long retentionHours = clamp(
            configService.getLong("traffic-logs-retention-hours", DEFAULT_RETENTION_HOURS),
            MIN_RETENTION_HOURS,
            MAX_RETENTION_HOURS);
        Duration retention = Duration.ofHours(retentionHours);

        return serializeLog(logEntry)
                .flatMap(json -> listOps().leftPush(RedisKeys.TRAFFIC_LOG_LIST, json))
            .flatMap(count -> Mono.when(
                listOps().trim(RedisKeys.TRAFFIC_LOG_LIST, 0, maxEntries - 1),
                redisTemplate.expire(RedisKeys.TRAFFIC_LOG_LIST, retention)).then())
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to append traffic log: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<String> serializeLog(TrafficLog logEntry) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(logEntry));
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}

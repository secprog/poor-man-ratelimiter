package com.example.gateway.service;

import com.example.gateway.model.SystemConfig;
import com.example.gateway.store.SystemConfigStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConfigurationService {
    private final SystemConfigStore configStore;

    private final Cache<String, String> configCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

        private static final Map<String, String> DEFAULT_CONFIGS = Map.ofEntries(
            Map.entry("antibot-enabled", "true"),
            Map.entry("antibot-min-submit-time", "2000"),
            Map.entry("antibot-honeypot-field", "_hp_email"),
            Map.entry("session-cookie-name", "JSESSIONID"),
            Map.entry("antibot-challenge-type", "metarefresh"),
            Map.entry("antibot-metarefresh-delay", "3"),
            Map.entry("antibot-preact-difficulty", "1"),
            Map.entry("analytics-retention-days", "7"),
            Map.entry("traffic-logs-retention-hours", "24"),
            Map.entry("traffic-logs-max-entries", "10000")
        );

    @PostConstruct
    public void init() {
        configStore.putDefaults(DEFAULT_CONFIGS)
                .then(refreshCache())
                .subscribe();
    }

    public Mono<Void> refreshCache() {
        return configStore.findAll()
                .doOnNext(config -> configCache.put(config.getConfigKey(), config.getConfigValue()))
                .then();
    }

    public String getConfig(String key, String defaultValue) {
        String value = configCache.getIfPresent(key);
        return value != null ? value : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getConfig(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    public long getLong(String key, long defaultValue) {
        String value = getConfig(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        String value = getConfig(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public Flux<SystemConfig> getAllConfigs() {
        return configStore.findAll();
    }

    public Mono<SystemConfig> updateConfig(String key, String value) {
        return configStore.findById(key)
                .defaultIfEmpty(new SystemConfig(key, value))
                .flatMap(config -> {
                    config.setConfigValue(value);
                    return configStore.save(config);
                })
                .doOnSuccess(c -> configCache.put(c.getConfigKey(), c.getConfigValue()));
    }
}

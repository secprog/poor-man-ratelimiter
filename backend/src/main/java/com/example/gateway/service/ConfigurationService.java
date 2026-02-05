package com.example.gateway.service;

import com.example.gateway.model.SystemConfig;
import com.example.gateway.repository.SystemConfigRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigurationService {
    private final SystemConfigRepository repository;

    private final Cache<String, String> configCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    @PostConstruct
    public void init() {
        refreshCache().subscribe();
    }

    public Mono<Void> refreshCache() {
        return repository.findAll()
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

    public Flux<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    public Mono<SystemConfig> updateConfig(String key, String value) {
        return repository.findById(key)
                .defaultIfEmpty(new SystemConfig(key, value))
                .flatMap(config -> {
                    config.setConfigValue(value);
                    return repository.save(config);
                })
                .doOnSuccess(c -> configCache.put(c.getConfigKey(), c.getConfigValue()));
    }
}

package com.example.admin.controller;

import com.example.admin.model.SystemConfig;
import com.example.admin.service.SystemConfigStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/poormansRateLimit/api/admin/config")
@RequiredArgsConstructor
@Slf4j
public class SystemConfigController {

    private final SystemConfigStore configStore;

    @GetMapping
    public Flux<SystemConfig> getAllConfigs() {
        return configStore.findAll();
    }

    @PostMapping("/{key}")
    public Mono<SystemConfig> updateConfig(
            @PathVariable String key,
            @RequestBody ConfigValue configValue) {
        return configStore.save(key, configValue.value)
                .doOnSuccess(saved -> log.info("Updated config: {} = {}", key, configValue.value));
    }

    @DeleteMapping("/{key}")
    public Mono<Void> deleteConfig(@PathVariable String key) {
        return configStore.delete(key);
    }

    public static class ConfigValue {
        public String value;
    }
}

package com.example.gateway.controller;

import com.example.gateway.filter.AntiBotFilter;
import com.example.gateway.model.SystemConfig;
import com.example.gateway.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemConfigController {

    private final ConfigurationService configService;

    @GetMapping
    public Flux<SystemConfig> getAllConfigs() {
        return configService.getAllConfigs();
    }

    @PostMapping("/{key}")
    public Mono<SystemConfig> updateConfig(@PathVariable String key, @RequestBody Map<String, String> payload) {
        String value = payload.get("value");
        return configService.updateConfig(key, value);
    }
}

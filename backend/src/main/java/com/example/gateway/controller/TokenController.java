package com.example.gateway.controller;

import com.example.gateway.filter.AntiBotFilter;
import com.example.gateway.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TokenController {

    private final AntiBotFilter antiBotFilter;
    private final ConfigurationService configService;

    /**
     * Generate a new one-time form token for anti-bot protection.
     * Clients should request this before displaying a form and include
     * it in the form submission.
     */
    @GetMapping("/form")
    public Mono<Map<String, Object>> generateFormToken() {
        String token = antiBotFilter.generateFormToken();
        long loadTime = System.currentTimeMillis();
        String honeypotField = configService.getConfig("antibot-honeypot-field", "_hp_email");

        return Mono.just(Map.of(
                "token", token,
                "loadTime", loadTime,
                "honeypotField", honeypotField,
                "expiresIn", 600 // Token expires in 10 minutes (600 seconds)
        ));
    }
}

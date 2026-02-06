package com.example.admin.controller;

import com.example.admin.model.AnalyticsUpdate;
import com.example.admin.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/poormansRateLimit/api/admin/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public Mono<AnalyticsUpdate> getSummary() {
        return analyticsService.getLatestUpdate();
    }
}

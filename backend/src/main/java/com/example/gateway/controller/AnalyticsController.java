package com.example.gateway.controller;

import com.example.gateway.model.TrafficLog;
import com.example.gateway.service.AnalyticsService;
import com.example.gateway.store.TrafficLogStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/poormansRateLimit/api/admin/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TrafficLogStore trafficLogStore;

    @GetMapping("/summary")
    public Mono<AnalyticsService.StatsSummary> getSummary() {
        return analyticsService.getSummary();
    }
    
    @GetMapping("/timeseries")
    public Mono<List<AnalyticsService.TimeSeriesDataPoint>> getTimeSeries(
            @RequestParam(defaultValue = "24") int hours) {
        return analyticsService.getTimeSeries(hours);
    }

    @GetMapping("/traffic")
    public Mono<List<TrafficLog>> getRecentTraffic(
            @RequestParam(defaultValue = "50") int limit) {
        return trafficLogStore.getRecentLogs(limit);
    }
}

package com.example.admin.websocket;

import com.example.admin.model.AnalyticsUpdate;
import com.example.admin.service.AnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class AnalyticsBroadcaster {

    private final CopyOnWriteArrayList<FluxSink<AnalyticsUpdate>> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalyticsService analyticsService;

    public AnalyticsBroadcaster(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public void addSession(FluxSink<AnalyticsUpdate> sink) {
        sessions.add(sink);
        log.info("Analytics session added. Total sessions: {}", sessions.size());
    }

    public void removeSession(FluxSink<AnalyticsUpdate> sink) {
        sessions.remove(sink);
        log.info("Analytics session removed. Total sessions: {}", sessions.size());
    }

    public Flux<AnalyticsUpdate> getUpdates() {
        return Flux.<AnalyticsUpdate>create(sink -> {
            addSession(sink);
            sink.onDispose(() -> removeSession(sink));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public void broadcast(AnalyticsUpdate update) {
        sessions.forEach(sink -> {
            try {
                if (!sink.isCancelled()) {
                    sink.next(update);
                }
            } catch (Exception e) {
                log.warn("Error broadcasting to session", e);
            }
        });
    }

    // Periodically broadcast analytics updates to all connected WebSocket clients
    @Scheduled(fixedRate = 2000)
    public void broadcastAnalytics() {
        if (sessions.isEmpty()) {
            return; // No connected clients
        }
        
        analyticsService.getLatestUpdate()
                .subscribe(
                        update -> broadcast(update),
                        error -> log.error("Error getting analytics update for broadcast", error)
                );
    }
}

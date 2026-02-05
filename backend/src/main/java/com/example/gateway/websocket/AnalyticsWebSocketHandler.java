package com.example.gateway.websocket;

import com.example.gateway.dto.AnalyticsSnapshot;
import com.example.gateway.dto.AnalyticsWebSocketMessage;
import com.example.gateway.service.AnalyticsService;
import com.example.gateway.store.TrafficLogStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsWebSocketHandler implements WebSocketHandler {
    
    private final AnalyticsBroadcaster broadcaster;
    private final AnalyticsService analyticsService;
    private final TrafficLogStore trafficLogStore;
    private final ObjectMapper objectMapper;
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket connection opened: {}", session.getId());
        broadcaster.addSession(session);

        Mono<WebSocketMessage> snapshotMessage = Mono.zip(
                analyticsService.getCurrentUpdate(),
                analyticsService.getTimeSeries(24),
                trafficLogStore.getRecentLogs(100))
                .map(tuple -> new AnalyticsSnapshot(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .map(snapshot -> new AnalyticsWebSocketMessage("snapshot", snapshot))
                .flatMap(message -> Mono.fromCallable(() -> objectMapper.writeValueAsString(message)))
                .map(session::textMessage)
                .onErrorResume(error -> {
                    log.warn("Failed to send analytics snapshot", error);
                    return Mono.empty();
                });
        
        // Keep the connection alive and handle incoming messages
        Mono<Void> incoming = session.receive()
                .doOnNext(message -> {
                    // We don't process incoming messages, just for keeping connection alive
                })
                .doFinally(signal -> {
                    log.info("WebSocket connection closed: {} - {}", session.getId(), signal);
                    broadcaster.removeSession(session);
                })
                .then();

        return session.send(snapshotMessage).then(incoming);
    }
}


package com.example.gateway.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsWebSocketHandler implements WebSocketHandler {
    
    private final AnalyticsBroadcaster broadcaster;
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        log.info("WebSocket connection opened: {}", session.getId());
        broadcaster.addSession(session);
        
        // Keep the connection alive and handle incoming messages
        return session.receive()
                .doOnNext(message -> {
                    // We don't process incoming messages, just for keeping connection alive
                })
                .doFinally(signal -> {
                    log.info("WebSocket connection closed: {} - {}", session.getId(), signal);
                    broadcaster.removeSession(session);
                })
                .then();
    }
}


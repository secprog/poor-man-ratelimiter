package com.example.gateway.websocket;

import com.example.gateway.dto.AnalyticsUpdate;
import com.example.gateway.dto.AnalyticsWebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsBroadcaster {
    
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;
    
    public void addSession(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket session added. Total sessions: {}", sessions.size());
    }
    
    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
        log.info("WebSocket session removed. Total sessions: {}", sessions.size());
    }
    
    public void broadcast(AnalyticsUpdate update) {
        broadcastMessage("summary", update);
    }

    public void broadcastMessage(String type, Object payload) {
        String message;
        try {
            message = objectMapper.writeValueAsString(new AnalyticsWebSocketMessage(type, payload));
        } catch (Exception e) {
            log.error("Failed to serialize analytics WebSocket message", e);
            return;
        }

        broadcast(message);
    }
    
    public void broadcast(String message) {
        sessions.forEach(session -> {
            if (session.isOpen()) {
                WebSocketMessage msg = session.textMessage(message);
                session.send(Flux.just(msg))
                        .doOnError(error -> {
                            log.warn("Failed to send message to session, removing it", error);
                            removeSession(session);
                        })
                        .subscribe();
            }
        });
    }
    
    public int getConnectedSessions() {
        return sessions.size();
    }
}


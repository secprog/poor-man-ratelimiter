package com.example.gateway.config;

import com.example.gateway.websocket.AnalyticsWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {
    
    private final AnalyticsWebSocketHandler analyticsWebSocketHandler;
    
    /**
     * SECURITY: WebSocket handler for admin port (9090 only).
     * NOT registered on port 8080 because ApiPortFilter blocks /poormansRateLimit/api/admin/** paths there.
     * 
     * The admin server on port 9090 will use this mapping through Spring Context.
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, WebSocketHandler> map = new HashMap<>();
        // Register ONLY on the admin path (will be accessed via port 9090)
        map.put("/poormansRateLimit/api/admin/ws/analytics", analyticsWebSocketHandler);
        
        SimpleUrlHandlerMapping handlerMapping = new SimpleUrlHandlerMapping();
        handlerMapping.setUrlMap(map);
        handlerMapping.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return handlerMapping;
    }
    
    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}


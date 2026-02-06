package com.example.admin.config;

import com.example.admin.websocket.AnalyticsWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {

    @Bean
    public SimpleUrlHandlerMapping webSocketHandlerMapping(
            AnalyticsWebSocketHandler analyticsWebSocketHandler) {
        
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/poormansRateLimit/api/admin/ws/analytics", analyticsWebSocketHandler);
        
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(map);
        mapping.setOrder(10);
        return mapping;
    }
}

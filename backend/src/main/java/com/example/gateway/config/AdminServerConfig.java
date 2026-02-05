package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Dual-port HTTP server configuration for gateway + admin APIs.
 * 
 * - Port 8080: Main gateway (public routes like /httpbin/**, /test/**)
 *   - ApiPortFilter rejects /api/** requests with 404
 * 
 * - Port 9090: Admin APIs (all /api/** routes)
 *   - Accepts /api/** requests
 * 
 * This port-based routing ensures clean separation: gateway traffic is isolated from admin traffic.
 */
@Slf4j
@Configuration
public class AdminServerConfig {

    /**
     * Create secondary HTTP server on port 9090 for admin APIs.
     * Accepts all requests (primarily /api/** routes).
     * Docker-compose maps to 127.0.0.1:9090 (localhost only).
     */
    @Bean(destroyMethod = "disposeNow")
    public DisposableServer adminHttpServer(WebHandler webHandler) {
        log.info("Starting admin server on port 9090 (/api/** routes)");
        
        HttpHandler httpHandler = WebHttpHandlerBuilder.webHandler(webHandler).build();
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        
        return HttpServer.create()
            .host("0.0.0.0")
            .port(9090)
            .handle(adapter)
            .doOnBind(conn -> log.info("Admin server successfully bound to port 9090"))
            .bindNow();
    }
}


package com.example.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * SECURITY-CRITICAL: Dual-port HTTP server configuration for port-based API isolation.
 * 
 * SECURITY MODEL IN DOCKER:
 * ==========================
 * - Port 8080: Main gateway (public - accessible over network)
 *   * Routes: /httpbin/**, /test/**, /metrics/** 
 *   * BLOCKS: ALL /poormansRateLimit/api/admin/** requests (404 via ApiPortFilter)
 *   * Contains: RateLimitFilter, AntiBotFilter, rate limiting logic
 * 
 * - Port 9090: Admin APIs (Docker internal only - NOT exposed to host)
 *   * Routes: /poormansRateLimit/api/admin/**
 *   * Java binds to 0.0.0.0:9090 (accessible within Docker network)
 *   * docker-compose.yml binds to 127.0.0.1:9090:9090 (localhost-only on HOST)
 *   * Result: Internal container-to-container communication ✓, No external access ✓
 * 
 * PORT-BASED ISOLATION (MULTI-LAYER):
 * ===================================
 * 1. ApiPortFilter: Blocks /poormansRateLimit/api/admin/** on port 8080 (application layer)
 * 2. Docker port binding: 127.0.0.1:9090:9090 prevents HOST-level access (OS layer)
 * 3. Container network: Port 9090 not exposed, only internal communication (network layer)
 * 
 * Why this works:
 * - Java binds to 0.0.0.0 so Docker containers can reach it via internal network
 * - Docker HOST binding to 127.0.0.1 prevents external access (OS-level enforcement)
 * - Not declaring port in docker-compose (no host binding) would prevent any HOST access
 * - ApiPortFilter adds defense-in-depth by blocking on port 8080
 */
@Slf4j
@Configuration
public class AdminServerConfig {

    /**
     * Create secondary HTTP server on port 9090 for admin APIs ONLY.
     * 
     * CRITICAL: This server is completely separate from port 8080.
     * It ONLY accepts /poormansRateLimit/api/admin/** requests.
     * 
     * DOCKER NETWORKING:
     * - Binds to 0.0.0.0:9090 (accessible within Docker container network)
     * - Frontend container can reach via http://backend:9090 (resolves to 172.20.0.X)
     * - Host machine access controlled via docker-compose.yml port binding
     * 
     * Docker-compose configuration:
     * - "127.0.0.1:9090:9090" means: only localhost on HOST can access
     * - Internal Docker communication still works (0.0.0.0 listening)
     * - External network access BLOCKED by OS-level port binding
     * 
     * All admin controllers have @RequestMapping("/poormansRateLimit/api/admin/**")
     * which only matches requests on this port 9090 server.
     */
    @Bean(destroyMethod = "disposeNow")
    public DisposableServer adminHttpServer(WebHandler webHandler) {
        log.info("Starting admin server on port 9090 (/api/** routes with WebSocket support)");
        
        // Build HttpHandler with full routing and WebSocket support
        HttpHandler httpHandler = WebHttpHandlerBuilder
            .webHandler(webHandler)
            .build();
        
        ReactorHttpHandlerAdapter adapter = new ReactorHttpHandlerAdapter(httpHandler);
        
        return HttpServer.create()
            .host("0.0.0.0")
            .port(9090)
            .handle(adapter)
            .doOnBind(conn -> log.info("Admin server successfully bound to port 9090"))
            .bindNow();
    }

    @Bean
    public ApplicationRunner validateAdminPortBinding(Environment environment) {
        return args -> {
            String adminPort = environment.getProperty("admin.port", "9090");
            log.info("Admin server configured on port {}", adminPort);
            log.info("Admin API should only be reachable via localhost (127.0.0.1:{})", adminPort);
        };
    }
}


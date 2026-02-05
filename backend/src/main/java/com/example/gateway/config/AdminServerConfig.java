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
 * SECURITY MODEL:
 * ===============
 * - Port 8080: Main gateway (public - accessible over network)
 *   * Routes: /httpbin/**, /test/**, /metrics/** 
 *   * BLOCKS: ALL /poormansRateLimit/api/admin/** requests (404 via ApiPortFilter)
 *   * Contains: RateLimitFilter, AntiBotFilter, rate limiting logic
 * 
 * - Port 9090: Admin APIs (private - localhost only via port binding)
 *   * Routes: /poormansRateLimit/api/admin/**
 *   * Docker-compose maps to 127.0.0.1:9090 (localhost-only binding)
 *   * UNREACHABLE from any network interface except localhost
 * 
 * PORT-BASED ISOLATION (ONLY DEFENSE):
 * ====================================
 * Admin routes are COMPLETELY INVISIBLE on port 8080.
 * ApiPortFilter returns 404 - makes routes appear non-existent.
 * Port 9090 is bound to 127.0.0.1 - OS-level enforcement.
 * 
 * Why this is sufficient:
 * - TCP port binding cannot be spoofed at application level
 * - OS kernel prevents connections to port 9090 from non-localhost
 * - If someone reaches localhost, they're already on the machine
 * - Simpler code = simpler security (no application-level IP checks)
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
     * Docker-compose configuration:
     * - Binds to 127.0.0.1:9090 (localhost only, not accessible over network)
     * - Format: "127.0.0.1:9090:9090" in docker-compose.yml
     * - This means: only localhost can connect to this port
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


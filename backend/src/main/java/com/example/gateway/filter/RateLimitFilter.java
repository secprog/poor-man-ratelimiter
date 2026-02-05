package com.example.gateway.filter;

import com.example.gateway.dto.RateLimitResult;
import com.example.gateway.service.JwtService;
import com.example.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String CACHED_BODY_ATTRIBUTE = "cachedRequestBody";

    private final RateLimiterService rateLimiterService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        
        // Extract Authorization header for potential JWT-based rate limiting
        String authHeader = JwtService.extractAuthorizationHeader(exchange.getRequest().getHeaders());

        // Check if this is a request with a body (POST, PUT, PATCH)
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().toString() : "";
        boolean hasBody = ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));
        
        // If request has body and is JSON, read it for body-based rate limiting
        if (hasBody && isJsonContent(exchange)) {
            return readBody(exchange)
                    .then(performRateLimitCheck(exchange, chain, path, ip, authHeader));
        } else {
            // No body or not JSON, proceed directly
            return performRateLimitCheck(exchange, chain, path, ip, authHeader);
        }
    }

    /**
     * Check if the request content-type is JSON
     */
    private boolean isJsonContent(ServerWebExchange exchange) {
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        return contentType != null && contentType.contains("application/json");
    }

    /**
     * Read and cache the request body for later access by the rate limiter service
     */
    private Mono<Void> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .doOnNext(dataBuffer -> {
                    // Cache the body bytes
                    byte[] bodyBytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bodyBytes);
                    DataBufferUtils.release(dataBuffer);
                    
                    // Store in exchange attributes for later use by rate limiter service
                    exchange.getAttributes().put(CACHED_BODY_ATTRIBUTE, bodyBytes);
                })
                .onErrorResume(e -> {
                    log.debug("Failed to read request body: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Perform the actual rate limit check
     */
    private Mono<Void> performRateLimitCheck(ServerWebExchange exchange, GatewayFilterChain chain,
                                              String path, String ip, String authHeader) {
        // Pass the cached body to the rate limiter service
        byte[] cachedBody = exchange.getAttribute(CACHED_BODY_ATTRIBUTE);
        
        return rateLimiterService.isAllowed(exchange, path, ip, authHeader, cachedBody)
                .flatMap(result -> {
                    if (!result.isAllowed()) {
                        // Rate limit exceeded and no queueing
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        if (result.isQueued()) {
                            exchange.getResponse().getHeaders().add("X-RateLimit-Queued", "true");
                        }
                        return exchange.getResponse().setComplete();
                    }
                    
                    // Add headers to indicate queue status
                    if (result.isQueued()) {
                        exchange.getResponse().getHeaders().add("X-RateLimit-Queued", "true");
                        exchange.getResponse().getHeaders().add("X-RateLimit-Delay-Ms", String.valueOf(result.getDelayMs()));
                    }
                    
                    // Apply delay if queued
                    if (result.getDelayMs() > 0) {
                        log.debug("Delaying request to {} from {} by {}ms", path, ip, result.getDelayMs());
                        return Mono.delay(Duration.ofMillis(result.getDelayMs()))
                                .then(chain.filter(exchange));
                    } else {
                        return chain.filter(exchange);
                    }
                });
    }

    @Override
    public int getOrder() {
        return -1; // High priority
    }
}

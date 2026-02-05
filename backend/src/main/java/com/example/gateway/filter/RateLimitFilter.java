package com.example.gateway.filter;

import com.example.gateway.dto.RateLimitResult;
import com.example.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimiterService rateLimiterService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        return rateLimiterService.isAllowed(path, ip)
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

package com.example.gateway.filter;

import com.example.gateway.service.JwtService;
import com.example.gateway.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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

        // Extract Authorization header for potential JWT-based rate limiting
        String authHeader = JwtService.extractAuthorizationHeader(exchange.getRequest().getHeaders());

        // Check if this is a request with a body (POST, PUT, PATCH)
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().toString() : "";
        boolean hasBody = ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method));

        // If request has body, read it for body-based rate limiting
        if (hasBody) {
            return ServerWebExchangeUtils.cacheRequestBody(exchange, serverHttpRequest -> {
                ServerWebExchange cachedExchange = exchange.mutate().request(serverHttpRequest).build();
                return performRateLimitCheck(cachedExchange, chain, path, ip, authHeader);
            });
        }

        // No body, proceed directly
        return performRateLimitCheck(exchange, chain, path, ip, authHeader);
    }

    /**
     * Perform the actual rate limit check
     */
    private Mono<Void> performRateLimitCheck(ServerWebExchange exchange, GatewayFilterChain chain,
                                             String path, String ip, String authHeader) {
        byte[] cachedBody = null;
        Object cached = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
        if (cached instanceof byte[]) {
            cachedBody = (byte[]) cached;
        } else if (cached instanceof DataBuffer) {
            DataBuffer buffer = (DataBuffer) cached;
            DataBufferUtils.retain(buffer);
            var byteBuffer = buffer.asByteBuffer().asReadOnlyBuffer();
            cachedBody = new byte[byteBuffer.remaining()];
            byteBuffer.get(cachedBody);
        }

        return rateLimiterService.isAllowed(exchange, path, ip, authHeader, cachedBody)
                .flatMap(result -> {
                    if (!result.isAllowed()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        if (result.isQueued()) {
                            exchange.getResponse().getHeaders().add("X-RateLimit-Queued", "true");
                        }
                        return exchange.getResponse().setComplete();
                    }

                    if (result.isQueued() && result.getDelayMs() > 0) {
                        log.debug("Delaying request by {}ms for rate limiting", result.getDelayMs());
                        return Mono.delay(Duration.ofMillis(result.getDelayMs()))
                                .then(Mono.defer(() -> {
                                    exchange.getResponse().getHeaders().add("X-RateLimit-Queued", "true");
                                    exchange.getResponse().getHeaders().add("X-RateLimit-Delay-Ms", String.valueOf(result.getDelayMs()));
                                    return chain.filter(exchange);
                                }));
                    }

                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return -1; // High priority
    }
}

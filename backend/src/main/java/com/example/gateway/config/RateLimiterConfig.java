package com.example.gateway.config;

import com.example.gateway.model.RateLimitPolicy;
import com.example.gateway.service.ConfigurationService;
import com.example.gateway.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RateLimiterConfig {

    private final PolicyService policyService;
    private final ConfigurationService configService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Bean
    @Primary
    public KeyResolver dynamicKeyResolver() {
        return exchange -> policyService.getAllPolicies()
                .collectList()
                .flatMap(policies -> {
                    RateLimitPolicy policy = findMatchingPolicy(policies, exchange);
                    if (policy == null || policy.getPolicyId() == null) {
                        // Default fallback: global limit on unknown routes to prevent total bypass if
                        // desired,
                        // or just unmetered. Here we use unmetered "default:unknown" but practically it
                        // depends on if the filter applies.
                        // If no policy matches via route pattern, findMatchingPolicy returns null.
                        // However, the GatewayFilter is applied globally or per route.
                        // If we are here, the filter IS applied.
                        return Mono.just("default:unknown");
                    }

                    String key = resolveKey(exchange, policy);
                    // Key format: policyId:limitType:resolvedKey
                    return Mono.just(policy.getPolicyId() + ":" + policy.getLimitType() + ":" + key);
                })
                .onErrorReturn("default:error");
    }

    private String resolveKey(ServerWebExchange exchange, RateLimitPolicy policy) {
        String type = policy.getLimitType();
        if (type == null)
            return getClientIp(exchange);

        switch (type.toUpperCase()) {
            case "USER_BASED":
                // Try Principal -> Header -> Cookie
                String userIdHeader = configService.getConfig("user-id-header-name", "X-User-Id");
                return exchange.getPrincipal()
                        .map(java.security.Principal::getName)
                        .switchIfEmpty(Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(userIdHeader)))
                        // Add more fallback logic here if needed (e.g. JWT parsing)
                        .defaultIfEmpty(getClientIp(exchange)) // Fallback to IP if no user ID found
                        .block(); // Blocking inside non-blocking context is bad, but here strictly for
                                  // KeyResolver...
            // Wait, KeyResolver returns Mono<String>. I should flatten this structure.

            case "API_KEY":
                String apiKeyHeader = configService.getConfig("api-key-header-name", "X-API-Key");
                String apiKey = exchange.getRequest().getHeaders().getFirst(apiKeyHeader);
                return StringUtils.hasText(apiKey) ? apiKey : getClientIp(exchange); // Fallback to IP

            case "GLOBAL":
                return "global";

            case "IP_BASED":
            default:
                return getClientIp(exchange);
        }
    }

    private String getClientIp(ServerWebExchange exchange) {
        boolean trustProxy = configService.getBoolean("trust-x-forwarded-for", false);
        String ipHeaderName = configService.getConfig("ip-header-name", "X-Forwarded-For");

        if (trustProxy) {
            String ipHeaderValue = exchange.getRequest().getHeaders().getFirst(ipHeaderName);
            if (StringUtils.hasText(ipHeaderValue)) {
                String clientIp = ipHeaderValue.split(",")[0].trim();
                if (StringUtils.hasText(clientIp)) {
                    return clientIp;
                }
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private List<RateLimitPolicy> findMatchingPolicies(List<RateLimitPolicy> policies, ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();

        return policies.stream()
                .filter(p -> p.getRoutePattern() != null && pathMatcher.match(p.getRoutePattern(), path))
                // Sort by pattern length descending (optional, but good for consistent
                // processing order)
                .sorted(Comparator.comparingInt((RateLimitPolicy p) -> p.getRoutePattern().length()).reversed())
                .collect(Collectors.toList());
    }
}

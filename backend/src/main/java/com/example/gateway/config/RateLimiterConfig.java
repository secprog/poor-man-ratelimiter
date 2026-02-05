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
            return getClientIp(exchange, policy);

        switch (type.toUpperCase()) {
            case "USER_BASED":
                // Try Principal -> Header (per-policy or global) -> Cookie
                String userIdHeader = policy.getHeaderName() != null 
                    ? policy.getHeaderName()
                    : configService.getConfig("user-id-header-name", "X-User-Id");
                return exchange.getPrincipal()
                        .map(java.security.Principal::getName)
                        .switchIfEmpty(Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(userIdHeader)))
                        // Add more fallback logic here if needed (e.g. JWT parsing)
                        .defaultIfEmpty(getClientIp(exchange, policy)) // Fallback to IP if no user ID found
                        .block();

            case "API_KEY":
                String apiKeyHeader = policy.getHeaderName() != null 
                    ? policy.getHeaderName()
                    : configService.getConfig("api-key-header-name", "X-API-Key");
                String apiKey = exchange.getRequest().getHeaders().getFirst(apiKeyHeader);
                return StringUtils.hasText(apiKey) ? apiKey : getClientIp(exchange, policy);

            case "SESSION_BASED":
                // Extract session cookie value (per-policy or global)
                String sessionCookieName = policy.getSessionCookieName() != null
                    ? policy.getSessionCookieName()
                    : configService.getConfig("session-cookie-name", "JSESSIONID");
                var sessionCookie = exchange.getRequest().getCookies().getFirst(sessionCookieName);
                if (sessionCookie != null && StringUtils.hasText(sessionCookie.getValue())) {
                    return sessionCookie.getValue();
                }
                return getClientIp(exchange, policy);

            case "GLOBAL":
                return "global";

            case "IP_BASED":
            default:
                return getClientIp(exchange, policy);
        }
    }

    private String getClientIp(ServerWebExchange exchange, RateLimitPolicy policy) {
        boolean trustProxy = policy.getTrustProxy() != null 
            ? policy.getTrustProxy()
            : configService.getBoolean("trust-x-forwarded-for", false);
        String ipHeaderName = policy.getHeaderName() != null
            ? policy.getHeaderName()
            : configService.getConfig("ip-header-name", "X-Forwarded-For");

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

    private RateLimitPolicy findMatchingPolicy(List<RateLimitPolicy> policies, ServerWebExchange exchange) {
        List<RateLimitPolicy> matching = findMatchingPolicies(policies, exchange);
        return !matching.isEmpty() ? matching.get(0) : null;
    }
}

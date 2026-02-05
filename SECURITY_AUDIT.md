# Security Audit Report

## Executive Summary

The project uses port-based isolation for admin APIs and routes analytics exclusively over a protected admin WebSocket. Major issues are fixed; a few hardening items remain:

1. FIXED: Admin WebSocket moved under /poormansRateLimit/api/admin/ws/analytics (port 9090)
2. FIXED: Admin CORS wildcard removed
3. FIXED: Redis/test/httpbin bound to localhost
4. FIXED: Redis now requires a password (REDIS_PASSWORD)
5. REMAINING: TLS and production config hardening

---

## Critical Security Issues

### 1. FIXED: Redis Authentication Added

Location: docker-compose.yml

Issue: Redis is localhost-only and now requires a password.

Applied fix:
```yaml
redis:
  command: ["redis-server", "--appendonly", "yes", "--requirepass", "${REDIS_PASSWORD:-dev-only-change-me}"]
```

Backend application.yml reads the same env var:
```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD:dev-only-change-me}
      host: redis
      port: 6379
```

Recommendation: Use a strong REDIS_PASSWORD in production (never the dev default).

---

## High-Priority Issues

### 2. FIXED: Admin CORS Wildcard Removed

Location: admin controllers
- RateLimitRuleController.java
- SystemConfigController.java

Status: Admin controllers no longer set @CrossOrigin.

---

### 3. FIXED: Misleading Code Comments

Location: docker-compose.yml and AdminServerConfig.java

Status: Comments now describe port-based isolation without IP-based filtering references.

---

### 4. FIXED: Test Server Localhost-Only

Location: docker-compose.yml

Status:
```yaml
test-server:
  ports:
    - "127.0.0.1:9000:9000"
```

---

### 5. FIXED: httpbin Localhost-Only

Location: docker-compose.yml

Status:
```yaml
httpbin:
  ports:
    - "127.0.0.1:8081:80"
```

---

## Medium-Priority Issues

### 6. FIXED: Admin Port Binding Clarity

Status: Admin port binding is logged at startup via ApplicationRunner.

---

### 7. FIXED: Analytics WebSocket Protected

Location: /poormansRateLimit/api/admin/ws/analytics (port 9090, localhost-only)

Current implementation: WebSocket messages are only available on the admin port and include:
- summary updates (allowed/blocked/activePolicies)
- snapshot (summary + 24h timeseries + latest traffic)
- traffic events (per request)

Recommendation: Keep payloads limited to aggregate metrics unless explicitly needed.

---

### 8. No TLS/HTTPS (Dev Only)

Status: Acceptable for development. Add TLS for production via reverse proxy or Spring SSL config.

---

## Access Control Model

Current model:
- Port 8080 (public): ApiPortFilter returns 404 for admin routes
- Port 9090 (private): OS-level TCP binding to 127.0.0.1

Assessment:
- Not bypassable by application code
- Not bypassable by header manipulation
- Reliant on docker-compose configuration

---

## Summary of Fixes Required

| Priority | Issue | Fix | Impact |
|----------|-------|-----|--------|
| MEDIUM | TLS in production | Add reverse proxy TLS | Production readiness |
| MEDIUM | Config hardening | Validate admin bind/port in infra | Reduce misconfig risk |

---

## Recommendations

Immediate (Do Now):
1. Replace dev-only-change-me with a real REDIS_PASSWORD in production

Short-term (Before Production):
1. Add TLS/HTTPS support
2. Review WebSocket payloads if new fields are added
3. Ensure admin port is never exposed externally in deployment manifests

---

## Bypass Scenario Testing

Test 1: Can I reach admin WebSocket on port 8080?
```bash
curl http://localhost:8080/poormansRateLimit/api/admin/ws/analytics
# Expected: 404 NOT_FOUND
```

Test 2: Can I reach admin WebSocket on port 9090 from non-localhost?
```bash
# From another host:
curl http://192.168.1.100:9090/poormansRateLimit/api/admin/ws/analytics
# Expected: Connection refused/timeout (no response)
```

Test 3: Can I access Redis without auth?
```bash
redis-cli -h 127.0.0.1 -p 6379 ping
# Expected: NOAUTH Authentication required
```

---

## Conclusion

Current Security Posture: B+

Strengths:
- Port-based isolation is solid
- Admin WebSocket is protected
- Redis/test/httpbin are localhost-only

Weaknesses:
- Reliant on docker-compose configuration for security
- TLS not configured (dev only)

Action Items: Set a production REDIS_PASSWORD and add TLS before production.

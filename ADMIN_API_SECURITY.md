# Admin API Security Model

## Overview

Admin APIs are protected using **port-based isolation** as the primary defense mechanism, with **localhost IP validation** as a secondary defense. This defense-in-depth approach ensures admin endpoints are truly inaccessible to unauthorized users.

## Port-Based Isolation (PRIMARY DEFENSE)

### How It Works

The application runs TWO separate HTTP servers on different ports:

```
Port 8080 (PUBLIC GATEWAY)
├── Routes: /httpbin/**, /test/**, /metrics/**
├── Filters:
│   ├── ApiPortFilter (HIGHEST_PRECEDENCE)
│   │   └── Any /poormansRateLimit/api/admin/** → 404 NOT_FOUND
│   ├── RateLimitFilter
│   └── AntiBotFilter
└── Admin controllers: NOT registered (appear non-existent)

Port 9090 (PRIVATE ADMIN SERVER)
├── Routes: /poormansRateLimit/api/admin/**
├── Controllers:
│   ├── AnalyticsController
│   ├── RateLimitRuleController
│   ├── SystemConfigController
│   ├── TokenController
│   └── AdminController
└── All requests validated by AdminAccessUtil.validateLocalhostOnly()
```

### Docker Network Binding

**CRITICAL**: Port 9090 is bound to localhost only:

```yaml
# docker-compose.yml backend service
ports:
  - "8080:8080"              # Maps to 0.0.0.0:8080 (accessible over network)
  - "127.0.0.1:9090:9090"    # Maps to 127.0.0.1:9090 (localhost only, NOT accessible over network)
```

This means:
- Port 9090 is **completely unreachable** from other hosts
- Port 9090 is **only accessible** from localhost
- No network request can reach port 9090 from outside the container/host machine

### ApiPortFilter Implementation

Located: [backend/src/main/java/com/example/gateway/filter/ApiPortFilter.java](backend/src/main/java/com/example/gateway/filter/ApiPortFilter.java)

```java
@Component
@Slf4j
public class ApiPortFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // ANY attempt to access admin routes on port 8080 gets 404
        if (path.startsWith("/poormansRateLimit/api/admin/")) {
            log.warn("SECURITY: Admin API access attempt on gateway port 8080 - blocking");
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;  // Run FIRST
    }
}
```

**Key Design Decisions:**
- Returns **404** (not 403) to make routes appear non-existent
- Runs with **HIGHEST_PRECEDENCE** to fail-fast
- Logs all attempts for security auditing
- Returns immediately without further processing

### Why Port-Based Isolation is Superior

| Defense | Bypassability | Notes |
|---------|---------------|-------|
| **Port 9090 binding to 127.0.0.1** | ✅ Not bypassable | TCP port binding is OS-level, cannot be spoofed at application level |
| IP filtering | ❌ Easily bypassable | Can spoof X-Forwarded-For, use proxies, etc. |
| URL path filtering | ❌ Easily bypassable | Can use encoding, path traversal, etc. |
| Application-level ACLs | ❌ Easily bypassable | Can be bypassed with protocol exploits |

**Result:** Requests to port 8080 **structurally cannot** reach admin endpoints. It's not a matter of filtering - the endpoints simply don't exist on that port at the TCP level.

## No IP-Based Filtering (Intentionally Removed)

### Why Not Used

IP filtering was **intentionally removed** because:

1. **Easily bypassable** - Can spoof X-Forwarded-For header, use proxies, protocol exploits
2. **False sense of security** - Gives appearance of protection that can't be relied upon
3. **Unnecessary complexity** - Port binding provides sufficient defense

**Port-based isolation is the real defense.** It cannot be spoofed at the application level because it's enforced by the operating system's TCP/IP stack.

### What We Trust

- ✅ OS-level TCP port binding (cannot be spoofed)
- ✅ Docker network isolation (enforced by container runtime)
- ❌ Application-level IP checks (easily bypassed)
- ❌ Header-based validation (can be spoofed by clients)

## Request Flow Diagram

### Legitimate Admin Request (from localhost)

```
localhost:9090/poormansRateLimit/api/admin/analytics/summary
        │
        └─→ [Docker] Port 9090 bound to 127.0.0.1:9090
            │
            ├─→ YES - Connection accepted from localhost
            │
            └─→ [Spring] Route to AnalyticsController
                │
                └─→ analyticsService.getSummary() executes
                    │
                    └─→ Return 200 OK + analytics data
```

### Unauthorized Request #1 (Port 8080 - Gateway)

```
attacker:8080/poormansRateLimit/api/admin/analytics/summary
        │
        └─→ [Docker] Port 8080 accepts from any host (0.0.0.0:8080)
            │
            └─→ [ApiPortFilter] Check path starts with /poormansRateLimit/api/admin/
                │
                └─→ YES - Path detected as admin route
                    │
                    └─→ RETURN 404 NOT_FOUND immediately
                        REQUEST BLOCKED
```

### Unauthorized Request #2 (Port 9090 - From Network)

```
attacker:9090/poormansRateLimit/api/admin/analytics/summary
        │
        └─→ [Docker] Port 9090 bound to 127.0.0.1:9090 ONLY
            │
            ├─→ Connection from non-localhost IP
            │
            └─→ Connection refused / Connection timeout
                "Address not accessible from your host"
                REQUEST BLOCKED AT TCP LEVEL (OS kernel)
```

## Security Checklist

- ✅ Port 8080: Bound to `0.0.0.0` (accessible over network)
- ✅ Port 9090: Bound to `127.0.0.1` (localhost only, NOT accessible over network)
- ✅ ApiPortFilter: Blocks all `/poormansRateLimit/api/admin/**` requests on port 8080 with 404
- ✅ All admin endpoints are **only registered** on port 9090 server
- ✅ No IP-based filtering in controllers (port binding is sufficient)
- ✅ Minimal code complexity - endpoints focus on business logic only

## Deployment Considerations

### Local Development

```bash
# Port 9090 is bound to 127.0.0.1:9090 - only accessible from your machine
curl http://127.0.0.1:9090/poormansRateLimit/api/admin/analytics/summary ✓
curl http://localhost:9090/poormansRateLimit/api/admin/analytics/summary ✓
curl http://192.168.1.100:9090/poormansRateLimit/api/admin/analytics/summary ✗ (Connection refused)
```

### Docker Compose

Port 9090 is explicitly bound to `127.0.0.1:9090`:

```yaml
ports:
  - "127.0.0.1:9090:9090"    # ← Only accessible from localhost
```

### Kubernetes / Cloud Deployment

If deploying to cloud:

1. **Do NOT expose port 9090** to any ingress or load balancer
2. **Network policies** should restrict port 9090 to pod-internal or bastion access
3. **Example Kubernetes NetworkPolicy:**

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: admin-api-protected
spec:
  podSelector:
    matchLabels:
      app: rate-limiter-gateway
  policyTypes:
    - Ingress
  ingress:
    # Allow port 8080 from any source (public gateway)
    - from:
        - ipBlock:
            cidr: 0.0.0.0/0
      ports:
        - protocol: TCP
          port: 8080
    # Allow port 9090 ONLY from localhost (pod loopback)
    - from:
        - podSelector:
            matchLabels:
              app: rate-limiter-gateway  # Only from same pod
      ports:
        - protocol: TCP
          port: 9090
```

## Testing

### Test 1: Admin Route Blocked on Port 8080

```bash
# This should return 404 (route made invisible)
curl -i http://localhost:8080/poormansRateLimit/api/admin/analytics/summary

# Expected response:
HTTP/1.1 404 Not Found
```

### Test 2: Admin Route Accessible on Port 9090 from Localhost

```bash
# This should return 200 and analytics data
curl -i http://localhost:9090/poormansRateLimit/api/admin/analytics/summary

# Expected response:
HTTP/1.1 200 OK
Content-Type: application/json
{
  "requestsAllowed": 1234,
  "requestsBlocked": 56,
  "activePolicies": 3
}
```

### Test 3: Port 9090 Unreachable from Network

From another host on the network:

```bash
# This should timeout or refuse connection
curl -i http://192.168.1.100:9090/poormansRateLimit/api/admin/analytics/summary

# Expected: Connection refused or timeout (NOT a response)
```

## Summary

The security model is straightforward and relies on **OS-level TCP port binding**:

| Layer | Defense | Level |
|-------|---------|-------|
| **Network (Docker)** | Port 9090 bound to 127.0.0.1 | ✅ PRIMARY (enforced by OS) |
| **Firewall (Spring)** | ApiPortFilter blocks on port 8080 | ✅ SECONDARY (all admin routes return 404) |

**That's it.** No application-level IP filtering. No false sense of security from headers.

### Why This Design Is Sound

1. **Port 9090 binding to 127.0.0.1 cannot be bypassed** - it's enforced by the operating system's TCP/IP stack
2. **If someone somehow reaches port 9090**, it means they already have localhost access (they're on the machine)
3. **ApiPortFilter ensures clean separation** - admin routes simply don't exist on the public gateway
4. **Simpler code** - controllers focus on business logic, not security theater

# Rate Limiter Gateway - AI Agent Guidelines

## Project Overview
Microservices API gateway: **Gateway Service** (public rate limiting) + **Admin Service** (isolated admin APIs) communicating via Redis only. React admin UI with real-time WebSocket analytics.

## Code Style

**Spring Boot (Both Services):**
- Always use **reactive types** (`Mono<T>`, `Flux<T>`) - see [gateway/src/.../service/RateLimiterService.java](gateway/src/main/java/com/example/gateway/service/RateLimiterService.java)
- Use **ReactiveRedisTemplate** for all Redis operations - never block
- Apply **Lombok** (`@RequiredArgsConstructor`, `@Slf4j`, `@Data`) consistently
- Thread-safe counters: `ConcurrentHashMap` + `AtomicInteger` with CAS loops (see RateLimiterService queue management)

**React Frontend:**
- Functional components only - see [frontend/src/pages/Dashboard.jsx](frontend/src/pages/Dashboard.jsx)
- Tailwind for all styling - no custom CSS files
- WebSocket via [frontend/src/utils/websocket.js](frontend/src/utils/websocket.js) wrapper (expects `{type, payload}` format)
## Architecture

**Service Separation (Critical):**
- **Gateway** (`/gateway/`, port 8080, public): Rate limiting + anti-bot → writes counters/logs to Redis
- **Admin** (`/backend/`, port 9090, localhost-only): CRUD APIs + WebSocket analytics → reads counters from Redis
- **No direct HTTP** between services - Redis is the only shared interface
- Folder names: `gateway/` builds as `gateway` service, `backend/` builds as `backend` service in docker-compose

**Key Patterns:**
- Rate limiting: [gateway/.../filter/RateLimitFilter.java](gateway/src/main/java/com/example/gateway/filter/RateLimitFilter.java) applies delays with `Mono.delay()` for queueing
- JWT extraction: [gateway/.../service/JwtService.java](gateway/src/main/java/com/example/gateway/service/JwtService.java) - **no signature verification** (assumes upstream auth)
- Analytics: [backend/.../websocket/AnalyticsBroadcaster.java](backend/src/main/java/com/example/admin/websocket/AnalyticsBroadcaster.java) broadcasts every 2s via reactive Flux
- WebSocket messages: Always wrap in `{type: "snapshot"|"summary", payload: {...}}` format (see [backend/.../dto/WebSocketMessage.java](backend/src/main/java/com/example/admin/dto/WebSocketMessage.java))

**Redis Keys:**
- `rate_limit_rules:<uuid>` - Rule configs (both services read/write)
- `request_counter:<rule>:<id>` - Gateway writes, Admin reads (TTL = window duration)
- `traffic_logs` - Gateway writes, Admin aggregates
- `system_config:<key>` - Settings (both read, Admin writes)

## Build and Test

### Docker (Recommended)
```bash
docker compose up --build              # All services
docker compose build gateway           # Rebuild gateway only  
docker compose build backend           # Rebuild admin service only
docker compose logs gateway -f         # Gateway logs
docker compose logs backend -f         # Admin service logs
```

### Local Development
```bash
# Gateway Service (requires Java 21, Maven, Redis on localhost:6379)
cd gateway
mvn clean package -DskipTests
java -jar target/*.jar                 # Port 8080

# Admin Service
cd backend
mvn clean package -DskipTests
java -jar target/*.jar                 # Port 9090

# Frontend (requires Node 18+)
cd frontend
npm install && npm run dev             # Port 5173 (dev) or build for production
```

### Testing
```bash
python run-tests.py                    # Automated suite (24+ tests)
.\Run-Tests.ps1                        # Windows PowerShell
python test-jwt-rate-limit.py         # JWT-specific tests
python test-body-content-types.py     # Body parsing tests
```

## Project Conventions

**Queue Management (Gateway):**
- Atomic queue depth: `ConcurrentHashMap<String, AtomicInteger>` with `compareAndSet()` loops
- Cleanup: `@Scheduled(fixedRate = 60000)` removes zero-depth entries
- Example: [gateway/.../service/RateLimiterService.java#startQueueCleanupTask](gateway/src/main/java/com/example/gateway/service/RateLimiterService.java)

**Anti-Bot Tokens:**
- Caffeine caches (10min TTL): `validTokens`, `usedTokens` (15min blacklist), `idempotencyKeys` (1hour)
- Always POST/PUT/PATCH only - see [gateway/.../filter/AntiBotFilter.java](gateway/src/main/java/com/example/gateway/filter/AntiBotFilter.java)
- Headers: `X-Form-Token`, `X-Time-To-Submit`, `X-Idempotency-Key`, honeypot field (empty)

**Model vs DTO:**
- `model/` = Persistent entities stored in Redis (RateLimitRule, RequestCounter, SystemConfig)
- `dto/` = Transient data transfer objects (RateLimitResult, WebSocketMessage, AnalyticsUpdate)

## Integration Points

**Nginx Proxy ([frontend/nginx.conf](frontend/nginx.conf)):**
```nginx
/poormansRateLimit/api/admin/**  →  http://backend:9090
/ws/**                           →  ws://backend:9090 (WebSocket)
```

**Gateway Routes ([gateway/.../resources/application.yml](gateway/src/main/resources/application.yml)):**
- Default test route: `/httpbin/**` → `https://httpbin.org/`
- Filters: `RateLimitFilter` (order: -1), `AntiBotFilter` (order: -100)

**Redis:** Reactive Lettuce driver, configure in `application.yml` → `spring.data.redis`

## Security

- **Port isolation**: Gateway public (8080), Admin localhost-only (`127.0.0.1:9090:9090` docker binding)
- **CORS**: Development uses `@CrossOrigin("*")` - **restrict in production**
- **JWT**: No signature verification in JwtService - validates claims only, assumes upstream already authenticated
- **Redis**: Password-protected (`REDIS_PASSWORD` env var), localhost-only binding in docker-compose

## Common Tasks

**Add rate limit rule:**
```bash
curl -X POST http://localhost:9090/poormansRateLimit/api/admin/rules \
  -H "Content-Type: application/json" \
  -d '{"pathPattern":"/api/**","allowedRequests":100,"windowSeconds":60,"active":true}'
```

**Enable queueing:**
```bash
curl -X PATCH http://localhost:9090/poormansRateLimit/api/admin/rules/{id}/queue \
  -d '{"queueEnabled":true,"maxQueueSize":10,"delayPerRequestMs":500}'
```

**Debug rate limits:**
```bash
docker compose logs gateway | grep "Loaded.*rate limit rules"
docker exec -it rate-limiter-gateway-redis-1 redis-cli -a dev-only-change-me KEYS "request_counter:*"
```

**Debug WebSocket:**
```bash
docker compose logs backend | grep WebSocket
# In browser DevTools: Network → WS → check message format {"type": "summary", "payload": {...}}
```

## Documentation
- **[README.md](README.md)** - User guide
- **[CODEBASE_OVERVIEW.md](CODEBASE_OVERVIEW.md)** - Architecture summary
- **[CLAUDE.md](CLAUDE.md)** - Detailed AI instructions
- **[QUEUEING_IMPLEMENTATION.md](QUEUEING_IMPLEMENTATION.md)** - Leaky bucket deep-dive
- **[JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)** - JWT guide

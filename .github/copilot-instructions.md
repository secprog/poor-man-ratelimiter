# Rate Limiter Gateway - Development Guidelines

## Project Overview
Production-ready API gateway with advanced rate limiting (token bucket + leaky bucket), anti-bot defenses, real-time WebSocket analytics, and comprehensive admin UI.

## Code Style

### Backend (Spring Boot 3 + WebFlux)
- **Always use reactive flows** (`Mono`, `Flux`) - avoid blocking operations in request paths
- Use **reactive Redis** for persistence, avoid blocking I/O
- Leverage **Caffeine cache** for tokens, counters, and frequently accessed data
- Apply **`@CrossOrigin`** on controllers for API endpoints
- Use **Lombok** annotations (`@RequiredArgsConstructor`, `@Slf4j`, `@Data`) to reduce boilerplate
- Log at appropriate levels: `debug` for detailed tracing, `info` for key events, `error` for failures

### Frontend (React 18 + Vite)
- **Functional components** with hooks (`useState`, `useEffect`, `useMemo`)
- **Tailwind CSS** for styling (utility-first approach)
- **Lucide React** for icons (`import { IconName } from 'lucide-react'`)
- **Recharts** for data visualization
- Centralize API calls in [frontend/src/api.js](frontend/src/api.js) (Axios instance)
- WebSocket connections use [frontend/src/utils/websocket.js](frontend/src/utils/websocket.js) wrapper

## Architecture

### Backend Structure

#### Filters (Spring Cloud Gateway)
- **[RateLimitFilter.java](backend/src/main/java/com/example/gateway/filter/RateLimitFilter.java)**: 
  - Token bucket + leaky bucket (queueing) enforcement
  - Handles `RateLimitResult` from `RateLimiterService`
  - Applies delays using `Mono.delay()` for queued requests
  - Adds headers: `X-RateLimit-Queued`, `X-RateLimit-Delay-Ms`
  - Order: `-1` (high priority, before routing)

- **[AntiBotFilter.java](backend/src/main/java/com/example/gateway/filter/AntiBotFilter.java)**:
  - Validates honeypot fields, time-to-submit, form tokens, idempotency keys
  - Uses Caffeine caches for token tracking (10min expiry)
  - Only applies to POST/PUT/PATCH requests
  - Order: `-100` (very early in filter chain)

#### Services
- **[RateLimiterService.java](backend/src/main/java/com/example/gateway/service/RateLimiterService.java)**:
  - Core rate limiting logic with `isAllowed(path, ip, authHeader)` returning `RateLimitResult`
  - Supports both IP-based and JWT-based rate limiting
  - Determines identifier based on rule configuration (JWT claims or IP)
  - Queue management with atomic `ConcurrentHashMap<String, AtomicInteger>` for depth tracking
  - CAS (Compare-And-Set) loops ensure thread-safe queue operations
  - Background cleanup task every 60 seconds removes stale queue entries
  - Returns: `RateLimitResult(allowed, delayMs, queued)`

- **[JwtService.java](backend/src/main/java/com/example/gateway/service/JwtService.java)**:
  - Extracts JWT claims from Authorization header without signature verification
  - Supports standard claims (sub, iss, aud, etc.) and custom claims
  - Concatenates multiple claims with configurable separator
  - Returns null for invalid tokens (triggers IP-based fallback)
  - NOTE: Does not validate JWT signatures (assumes upstream auth validated)

- **[ConfigurationService.java](backend/src/main/java/com/example/gateway/service/ConfigurationService.java)**:
  - Cached access to Redis `system_config` hash
  - Methods: `getString()`, `getInt()`, `getBoolean()` with defaults
  - Used by filters and services for runtime configuration

- **[AnalyticsService.java](backend/src/main/java/com/example/gateway/service/AnalyticsService.java)**:
  - Tracks allowed/blocked request counts
  - Logs traffic to Redis `traffic_logs` list (JSON entries)
  - Broadcasts updates via `AnalyticsBroadcaster` for WebSocket clients

#### Controllers
All controllers in [backend/src/main/java/com/example/gateway/controller](backend/src/main/java/com/example/gateway/controller):

- **[RateLimitRuleController.java](backend/src/main/java/com/example/gateway/controller/RateLimitRuleController.java)** (`/api/admin/rules`):
  - CRUD for rate limit rules
  - **`PATCH /{id}/queue`**: Update only queue settings (queueEnabled, maxQueueSize, delayPerRequestMs)
  - `POST /refresh`: Manually trigger rule cache reload

- **[TokenController.java](backend/src/main/java/com/example/gateway/controller/TokenController.java)** (`/api/tokens`):
  - `GET /form`: Issue form protection token
  - `GET /challenge`: Serve challenge page (meta refresh, JavaScript, or Preact)
  - Challenge type determined by `antibot-challenge-type` config

- **[SystemConfigController.java](backend/src/main/java/com/example/gateway/controller/SystemConfigController.java)** (`/api/config`):
  - `GET /`: List all configs
  - `POST /{key}`: Update single config value

- **[AnalyticsController.java](backend/src/main/java/com/example/gateway/controller/AnalyticsController.java)** (`/api/analytics`):
  - `GET /summary`: Current stats (allowed, blocked, activePolicies)
  - `GET /timeseries`: Historical data for charts

#### WebSocket
- **[AnalyticsWebSocketHandler.java](backend/src/main/java/com/example/gateway/websocket/AnalyticsWebSocketHandler.java)**: 
  - Endpoint: `/ws/analytics`
  - Sends real-time updates using `AnalyticsBroadcaster.getUpdates()` Flux

- **[AnalyticsBroadcaster.java](backend/src/main/java/com/example/gateway/websocket/AnalyticsBroadcaster.java)**:
  - Thread-safe Flux sink for broadcasting `AnalyticsUpdate` messages

#### Redis Storage
- **Rules/Policies**: `rate_limit_rules`, `rate_limit_policies` hashes
- **Counters**: `request_counter:<ruleId>:<identifier>` (JSON, TTL)
- **Token bucket state**: `rate_limit_state:<key>` (hash)
- **System config**: `system_config` hash
- **Analytics**: `request_stats:<minute>` hashes + `request_stats:index` zset
- **Traffic logs**: `traffic_logs` list (JSON)

### Frontend Structure

#### Pages
- **[Dashboard.jsx](frontend/src/pages/Dashboard.jsx)**:
  - Real-time stats from WebSocket + initial REST API load
  - Shows connection indicator (green dot with "Real-time")
  - Cards: Total Policies, Requests Allowed, Requests Blocked

- **[Analytics.jsx](frontend/src/pages/Analytics.jsx)**:
  - Recharts `LineChart` for time series data
  - Fetches `/api/analytics/timeseries`

- **[Policies.jsx](frontend/src/pages/Policies.jsx)**:
  - CRUD for rate limit rules
  - Queue settings UI (toggle, max size slider, delay input)
  - Uses `getFormToken()` and `getAntiBotHeaders()` for protected requests

- **[Settings.jsx](frontend/src/pages/Settings.jsx)**:
  - System config UI with grouped sections
  - Challenge type dropdown: `metarefresh`, `javascript`, `preact`
  - Difficulty/delay sliders for challenges
  - Save all or individual updates

#### Utils
- **[api.js](frontend/src/api.js)**: Axios instance with `baseURL: '/api'` (proxied by Nginx)
- **[formProtection.js](frontend/src/utils/formProtection.js)**: Anti-bot helpers
  - `getFormToken()`: Fetch token from `/api/tokens/form`
  - `getAntiBotHeaders(tokenData)`: Generate required headers
- **[websocket.js](frontend/src/utils/websocket.js)**: WebSocket client wrapper
  - Auto-reconnect logic
  - Subscribe/unsubscribe pattern

## Build and Test

### Docker (Primary Method)
```bash
docker compose up --build           # Full stack
docker compose logs backend -f      # Tail backend logs
docker compose down -v              # Clean shutdown with volume removal
```

### Local Development
```bash
# Backend (requires Java 17+, Maven)
cd backend
mvn clean package -DskipTests
java -jar target/*.jar

# Frontend (requires Node 18+)
cd frontend
npm install
npm run dev  # Vite dev server on port 5173
npm run build  # Production build to dist/
```

### Testing
```bash
# Automated test suite (24 tests)
python run-tests.py             # Cross-platform
.\\Run-Tests.ps1                # Windows PowerShell

# Manual testing
python test-server.py           # Terminal 1
python test-gateway.py          # Terminal 2
```

## Key Features & Implementation Details

### Rate Limiting Modes

#### Token Bucket (Immediate Rejection)
- Configured with `queueEnabled: false`
- Rejects excess requests with 429 status
- Implementation: `RateLimiterService.isAllowed()` checks Redis counter keys

#### Leaky Bucket (Request Queueing)
- Configured with `queueEnabled: true`, `maxQueueSize`, `delayPerRequestMs`
- Delays excess requests instead of rejecting
- Queue depth tracked atomically per rule+identifier
- CAS loop ensures no race conditions: `queueDepth.compareAndSet(current, current+1)`
- Background cleanup every 60s removes zero-depth entries
- Response headers: `X-RateLimit-Queued: true`, `X-RateLimit-Delay-Ms: <ms>`

#### JWT-Based Rate Limiting
- Configured with `jwtEnabled: true`, `jwtClaims` (JSON array), `jwtClaimSeparator`
- Extracts claims from Authorization header: `Bearer <token>`
- Supports standard claims (sub, iss, aud, exp, iat) and custom claims
- Concatenates multiple claims with separator (default: `:`)
- **Does NOT validate JWT signatures** - assumes upstream auth validated token
- Graceful fallback to IP-based limiting if:
  - Authorization header missing
  - Token is invalid or malformed
  - Specified claims don't exist in token
  - JWT claims configuration is invalid
- Example: Claims `["sub", "tenant_id"]` with values `user123` and `acme-corp` → identifier: `user123:acme-corp`
- See detailed docs: [JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)

### Anti-Bot Protection

#### Form Token Flow
1. Client: `GET /api/tokens/form` → receives `{token, timestamp}`
2. Client: Submits form with headers:
   - `X-Form-Token`: UUID token
   - `X-Time-To-Submit`: Milliseconds elapsed
   - `X-Idempotency-Key`: Optional duplicate prevention
   - Honeypot field: Must be empty
3. Server validates in `AntiBotFilter`:
   - Token exists in cache (10min TTL)
   - Token not already used (15min blacklist)
   - Time-to-submit > configured minimum (default: 2000ms)
   - Honeypot field matches configured name and is empty
   - Idempotency key not seen (1hour TTL)

#### Challenge Types
Configured via `antibot-challenge-type` in `system_config`:

- **`metarefresh`**: HTML meta tag with `http-equiv="refresh"` (no JavaScript required)
  - Delay: `antibot-metarefresh-delay` seconds
  - Use case: Accessibility, no-JS clients

- **`javascript`**: Client-side token generation
  - Validates token on POST request
  - Use case: Modern web applications

- **`preact`**: Lightweight React alternative
  - Sets `X-Form-Token-Challenge` cookie after delay
  - Auto-reload after `antibot-preact-difficulty` seconds
  - Use case: Progressive enhancement

### Real-time Analytics

#### WebSocket Flow
1. Frontend connects to `ws://backend:8080/ws/analytics`
2. `AnalyticsWebSocketHandler` subscribes to `AnalyticsBroadcaster.getUpdates()`
3. `AnalyticsService` calls `broadcaster.broadcast(update)` on each request
4. All connected clients receive `{requestsAllowed, requestsBlocked, activePolicies}` JSON

#### Traffic Logging
- Every request logged to Redis `traffic_logs` list (JSON entries)
- Time-series analytics stored in Redis `request_stats:*` hashes

## Integration Points

### Nginx Proxy (Frontend Container)
- Config: [frontend/nginx.conf](frontend/nginx.conf)
- Routes `/api/**` → `http://backend:8080/api/`
- Routes `/ws/**` → `http://backend:8080/ws/` (WebSocket)
- Serves static files from `/usr/share/nginx/html` (Vite build output)

### Gateway Routing
- Config: [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)
- Default route: `/httpbin/**` → `https://httpbin.org/` (for testing)
- Filters applied globally via `RateLimitFilter` and `AntiBotFilter`

### Redis Connection
- Host: `redis`, Port: `6379`
- Configure via `spring.data.redis` in [application.yml](backend/src/main/resources/application.yml)

## Security Considerations

### Sensitive Settings
- **`trust-x-forwarded-for`**: ONLY set to `true` behind trusted reverse proxy (default: `false`)
  - False: Use direct connection IP
  - True: Trust `X-Forwarded-For` header (opens IP spoofing vector)
  
- **`ip-header-name`**: Header to extract client IP from (default: `X-Forwarded-For`)

### CORS
- Controllers have `@CrossOrigin` for development convenience
- Production: Configure specific origins, not `*`

### Credentials
- Redis: Use ACLs/passwords and private networking in production
- Token secrets: Currently none (use UUIDs). Add HMAC/JWT signing in production.

## Common Tasks

### Adding a New Rate Limit Rule
1. **Via UI**: Policies page → Create button
2. **Via API**: `POST /api/admin/rules` with JSON body

### Enabling Queueing on Existing Rule
```bash
curl -X PATCH http://localhost:8080/api/admin/rules/{rule-id}/queue \\
  -H "Content-Type: application/json" \\
  -d '{"queueEnabled":true,"maxQueueSize":10,"delayPerRequestMs":500}'
```

### Enabling JWT-Based Rate Limiting
1. **Via UI**: Policies page → Edit rule → Enable JWT → Enter claims (comma-separated)
2. **Via API**: 
```bash
curl -X PUT http://localhost:8080/api/admin/rules/{rule-id} \\
  -H "Content-Type: application/json" \\
  -d '{
    "pathPattern": "/api/tenant/**",
    "allowedRequests": 100,
    "windowSeconds": 60,
    "active": true,
    "jwtEnabled": true,
    "jwtClaims": "[\"sub\", \"tenant_id\"]",
    "jwtClaimSeparator": ":"
  }'
```

### Testing JWT Rate Limiting
```bash
# Run automated test script
python test-jwt-rate-limit.py

# Manual testing with curl
TOKEN="eyJhbGc..."  # Your JWT token
for i in {1..10}; do
  curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/test
done
```

### Adding New System Config
1. Add config via `POST /api/admin/config/{key}` or Settings UI
2. Access in code: `configService.getString("my-key", "default-value")`
3. Expose in Settings UI: Add form field in [Settings.jsx](frontend/src/pages/Settings.jsx)

### Adding New Challenge Type
1. Add case in [TokenController.java](backend/src/main/java/com/example/gateway/controller/TokenController.java) `getChallenge()`
2. Return custom HTML with challenge logic
3. Update Settings UI dropdown in [Settings.jsx](frontend/src/pages/Settings.jsx)

### Debugging Rate Limit Issues
1. Check logs: `docker compose logs backend | grep -i "rate"`
2. Inspect counters: `redis-cli --scan --pattern "request_counter:*" | head -n 20`
3. Verify rule active: `GET /api/admin/rules/active`
4. Check queue depth in logs: Look for "Delaying request" messages

### Debugging JWT Rate Limiting Issues
1. Enable debug logging: `logging.level.com.example.gateway.service.JwtService: DEBUG`
2. Check JWT extraction: Look for "Extracted JWT claims" or "Failed to extract" in logs
3. Verify Authorization header: `curl -v -H "Authorization: Bearer <token>" <url>`
4. Decode JWT payload: `echo "<token>" | cut -d. -f2 | base64 -d | jq '.'`
5. Check fallback: Look for "falling back to IP" messages in logs
6. Verify claims configuration: `GET /api/admin/rules/{rule-id}`

## Performance Tuning

### Caffeine Cache Sizes
In [RateLimiterConfig.java](backend/src/main/java/com/example/gateway/config/RateLimiterConfig.java) or `AntiBotFilter.java`:
- `validTokens`: 100,000 entries (10min expiry)
- `usedTokens`: 100,000 entries (15min expiry)
- `idempotencyKeys`: 100,000 entries (1hour expiry)

Increase for high-traffic scenarios, monitor memory usage.

### Redis Connection
In [application.yml](backend/src/main/resources/application.yml):
```yaml
spring:
  data:
    redis:
      host: redis
      port: 6379
```

### Queue Cleanup Frequency
In [RateLimiterService.java](backend/src/main/java/com/example/gateway/service/RateLimiterService.java):
- Currently: `@Scheduled(fixedRate = 60000)` (60 seconds)
- Decrease for faster memory reclamation, increase to reduce overhead

## Documentation Files
- **[README.md](README.md)**: User-facing documentation
- **[QUEUEING_IMPLEMENTATION.md](QUEUEING_IMPLEMENTATION.md)**: Leaky bucket technical deep-dive
- **[JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)**: JWT-based rate limiting guide
- **[AUTOMATED_QUEUEING_TESTS.md](AUTOMATED_QUEUEING_TESTS.md)**: Test specifications for queueing feature
- **[CODEBASE_OVERVIEW.md](CODEBASE_OVERVIEW.md)**: Architectural summary
- **[TEST-README.md](TEST-README.md)**: Testing instructions

## Troubleshooting

### Backend Won't Start
- Check Redis connection: `docker compose logs redis`

### Frontend 502 Bad Gateway
- Backend not ready: `docker compose logs backend` for startup errors
- Nginx config issue: Check [nginx.conf](frontend/nginx.conf) proxy_pass URLs

### WebSocket Not Connecting
- Ensure `/ws/analytics` is proxied in Nginx (not just `/api/`)
- Check browser console for connection errors
- Verify `WebSocketConfig` registered in Spring Boot

### Rate Limits Not Enforcing
- Check rule active: `GET /api/admin/rules/active`
- Refresh cache: `POST http://localhost:8080/api/admin/rules/refresh`
- Verify IP extraction: Log `clientIp` in `RateLimitFilter`

### Tests Failing
- Run `docker compose up` in separate terminal before `python test-gateway.py`
- Check test server: `curl http://localhost:9000/test-endpoint`

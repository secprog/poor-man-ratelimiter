# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

API gateway with configurable rate limiting, anti-bot defenses, analytics, and admin UI.

**Stack:**
- Backend: Spring Boot 3 / Spring Cloud Gateway (reactive WebFlux), Redis (reactive), Caffeine caches
- Frontend: React 18 + Vite + Tailwind CSS, served via Nginx
- Infrastructure: Docker Compose (redis, backend, frontend, test-server)

## Common Commands

### Development with Docker (recommended)
```bash
# Start all services
docker compose up --build

# Rebuild specific service
docker compose build backend
docker compose up backend

# View logs
docker compose logs -f backend
```

### Backend Development
```bash
cd backend

# Build (skip tests)
mvn package -DskipTests

# Build with tests
mvn clean package

# Clean build artifacts
mvn clean
```

### Frontend Development
```bash
cd frontend

# Install dependencies
npm install

# Development server (requires Node 18+)
npm run dev

# Production build
npm run build

# Lint code
npm run lint
```

### Testing
```bash
# Run comprehensive test suite (requires gateway + test-server running)
python test-gateway.py

# Run tests via helper script (starts server + runs tests)
python run-tests.py

# Windows PowerShell
.\Run-Tests.ps1
```

## Architecture

### Backend Core Components

**Filters (request processing pipeline):**
- `filter/RateLimitFilter.java` - Token-bucket enforcement with queueing support; applies delays for queued requests
- `filter/AntiBotFilter.java` - Honeypot detection, timing checks, form tokens, idempotency keys, challenge cookie validation

**Controllers:**
- `controller/RateLimitRuleController.java` - Admin API for CRUD operations on rate limit rules (`/api/admin/rules`)
- `controller/TokenController.java` - Issues form tokens and anti-bot challenges (meta-refresh/JavaScript/Preact)
- `controller/AnalyticsController.java` - Provides analytics data and WebSocket endpoint
- `controller/SystemConfigController.java` - System configuration management

**Services:**
- `service/RateLimiterService.java` - Token-bucket logic with queue management; uses ConcurrentHashMap with AtomicInteger for thread-safe queue depth tracking
- `service/ConfigurationService.java` - Cached system config backed by Redis
- `service/PolicyService.java` - Rate limit policy management
- `service/AnalyticsService.java` - Traffic statistics and metrics

**Models & DTOs:**
- `model/RateLimitRule.java` - Rate limit rule with queueing fields (queueEnabled, maxQueueSize, delayPerRequestMs)
- `dto/RateLimitResult.java` - Contains allowed, delayMs, and queued status
- `model/SystemConfig.java` - Key-value configuration storage

**Data Layer:**
- Redis stores for reactive/non-blocking data access
- Defaults seeded on startup (`RedisBootstrapService`) and config defaults
- Redis connection configured in `application.yml` and `docker-compose.yml`

### Frontend Core Components

**Pages:**
- `pages/Dashboard.jsx` - Overview and quick stats
- `pages/Analytics.jsx` - Recharts visualizations + live WebSocket updates
- `pages/Policies.jsx` - Rate limit policy CRUD with anti-bot headers
- `pages/Settings.jsx` - System config including anti-bot challenge types and difficulty

**Utilities:**
- `api.js` - Centralized axios client for gateway API calls (points to `/api`, proxied by Nginx)
- `utils/websocket.js` - Real-time analytics WebSocket client (`/api/ws/analytics`)
- `utils/formProtection.js` - Token-based anti-bot form helpers

**Infrastructure:**
- Nginx config in `frontend/nginx.conf` proxies `/poormansRateLimit/api/` to backend:9090 and `/api/` + `/api/ws/` to backend:8080
- Build artifacts in `frontend/dist` served by Nginx container

### Rate Limiting & Queueing (Leaky Bucket)

**Token Bucket Implementation:**
- Enforced in `RateLimitFilter.java` via `RateLimiterService.checkRateLimit()`
- Per-policy/rule limits with configurable window and request count
- Caffeine caches for token buckets and rate limit counters

**Queueing Support:**
- When rate limit exceeded and queueing enabled, requests are delayed instead of rejected
- Queue depth tracked per rule+IP using `ConcurrentHashMap<String, AtomicInteger>`
- Thread-safe queue checking/incrementing via atomic compare-and-set (CAS) loop
- Delays calculated as `position * delayPerRequestMs`
- Background cleanup task removes stale queue entries every 60 seconds
- Response headers: `X-RateLimit-Queued: true`, `X-RateLimit-Delay-Ms: {delay}`

**Admin API for Queue Configuration:**
```bash
# Update queue settings for a rule (admin API on 9090)
PATCH /api/admin/rules/{id}/queue
{
  "queueEnabled": true,
  "maxQueueSize": 5,
  "delayPerRequestMs": 300
}

# Full rule CRUD also available
GET    /api/admin/rules          # List all
GET    /api/admin/rules/active   # Active only
POST   /api/admin/rules          # Create
PUT    /api/admin/rules/{id}     # Update
DELETE /api/admin/rules/{id}     # Delete
POST   /api/admin/rules/refresh  # Reload from DB
```

See `QUEUEING_IMPLEMENTATION.md` for detailed design notes.

### Anti-Bot Defenses

**Challenge Types** (configured in Settings UI):
- `metarefresh` - HTML meta refresh with configurable delay
- `javascript` - JavaScript-based token verification
- `preact` - Lightweight Preact challenge that sets cookie and reloads

**Protection Mechanisms:**
- Honeypot field detection (hidden form fields that bots fill)
- Time-to-submit validation (too fast = likely bot)
- One-time form tokens (prevents replay attacks)
- Idempotency keys (prevents duplicate submissions)
- Challenge cookies validated by `AntiBotFilter`

**Configuration Keys:**
- `antibot-challenge-type` - metarefresh|javascript|preact
- `antibot-metarefresh-delay` - seconds
- `antibot-preact-difficulty` - seconds before auto-refresh

### Reactive Architecture Notes

**Critical: Avoid blocking in request paths**
- This is a Spring WebFlux application; filters and services must be non-blocking
- Use reactive types: `Mono<T>`, `Flux<T>`
- Redis reactive APIs for async data access
- When adding delays, use `Mono.delay(Duration.ofMillis(x))` not `Thread.sleep()`
- Chain operations with `.flatMap()`, `.map()`, `.filter()` etc.

### Testing Infrastructure

**Test Suite** (`test-gateway.py`):
- 24 comprehensive tests covering rate limiting, anti-bot, queueing, routing
- Tests 21-24 are automated queueing tests (see `AUTOMATED_QUEUEING_TESTS.md`)
- Requires running gateway (http://localhost:8080) and test-server (http://localhost:9000)

**Test Server** (`test-server.py`):
- Simple Flask server exposing test endpoints
- Automatically started by `run-tests.py` or `docker-compose.yml`
- Listens on port 9000

## Service Ports

- Frontend: 3000 (Nginx serving React app)
- Gateway: 8080 (public routes)
- Admin API: 9090 (admin endpoints)
- Redis: 6379
- Test Server: 9000 (Flask)

## Security Considerations

- CORS configured at controller level
- Redis connection config in `application.yml` and `docker-compose.yml`
- Form tokens stored in Caffeine caches (in-memory, ephemeral)
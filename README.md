# Rate Limiter Gateway

Production-ready API gateway with advanced rate limiting, anti-bot defenses, real-time analytics, and comprehensive admin UI.

## 🚀 Key Features

### Rate Limiting
- **Token Bucket** algorithm with configurable replenish rates and burst capacity
- **Leaky Bucket (Queueing)** - delays requests instead of rejecting them during traffic spikes
  - Configurable queue size and per-request delay
  - Atomic queue depth tracking prevents race conditions
  - Automatic queue cleanup and monitoring
- **JWT-Based Rate Limiting** - rate limit by JWT token claims (user ID, tenant ID, custom claims)
  - Supports standard and custom JWT claims
  - Multi-claim concatenation for complex identifiers
  - Automatic fallback to IP-based limiting
- **Multiple Limit Types**: IP-based, JWT-based, session-based, custom headers
- **Per-route Configuration**: Different limits for different endpoints
- **Real-time Updates**: Changes propagate instantly via service layer

### Anti-Bot Protection
- **Honeypot Fields** - Hidden inputs that trap bots
- **Time-to-Submit Analysis** - Detects suspiciously fast form submissions
- **One-time Form Tokens** - Prevents replay attacks with Caffeine cache
- **Idempotency Keys** - Prevents duplicate submissions
- **Challenge Responses** - Three types:
  - **Meta Refresh**: HTML-only delay (no JavaScript required)
  - **JavaScript**: Client-side token generation and validation
  - **Preact**: Lightweight React alternative with auto-refresh
- **Configurable Protection Levels**: Enable/disable features per environment

### Real-time Analytics
- **WebSocket Live Updates**: Dashboard refreshes automatically as requests flow
- **Traffic Logging**: Persistent logs with timestamp, path, IP, status, and allow/block decision
- **Time Series Data**: Visualizations powered by Recharts
- **Request Counters**: Per-rule, per-IP tracking with automatic window cleanup
- **Summary Stats**: Allowed/blocked counts, active policies, queue depth

### Admin UI
- **Dashboard**: Real-time monitoring with WebSocket connection indicator
- **Policies**: CRUD interface for rate limit rules with live validation
- **Analytics**: Charts and graphs showing traffic patterns over time
- **Settings**: System configuration with immediate apply (no restart required)

## 🛠 Stack

- **Backend**: Spring Boot 3, Spring Cloud Gateway (WebFlux), R2DBC + Postgres, Caffeine caching
- **Frontend**: React 18, Vite, Tailwind CSS, Recharts, Lucide icons, served via Nginx
- **Database**: PostgreSQL 15 with R2DBC reactive driver
- **Infrastructure**: Docker Compose for orchestration, multi-stage builds

## 🏃 Quick Start

### Docker (Recommended)
```bash
docker compose up --build
# Frontend (Admin UI): http://localhost:3000
# Backend API:         http://localhost:8080
# Postgres:            localhost:5432
```

### Development Setup

#### Frontend
```bash
cd frontend
npm install
npm run dev  # Requires Node 18+
```

#### Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/gateway-application.jar
```

## 📚 Core Concepts

### Rate Limiting Modes

#### 1. Token Bucket (Immediate Rejection)
Traditional rate limiting that rejects requests when limits are exceeded:
```json
{
  "pathPattern": "/api/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "queueEnabled": false
}
```

#### 2. Leaky Bucket (Request Queueing)
Delays excess requests instead of rejecting them:
```json
{
  "pathPattern": "/api/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "queueEnabled": true,
  "maxQueueSize": 10,
  "delayPerRequestMs": 500
}
```

**Queueing Behavior**:
- First 100 requests in 60s: immediate ✅
- Next 10 requests: delayed (500ms, 1000ms, 1500ms...) ⏱️
- Beyond queue: rejected with 429 ❌

**Response Headers**:
```
X-RateLimit-Queued: true
X-RateLimit-Delay-Ms: 1500
```

#### 3. JWT-Based Rate Limiting
Rate limit based on JWT token claims instead of IP addresses:
```json
{
  "pathPattern": "/api/tenant/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "jwtEnabled": true,
  "jwtClaims": "[\"sub\", \"tenant_id\"]",
  "jwtClaimSeparator": ":"
}
```

**Features**:
- Extract standard (sub, iss, aud) and custom JWT claims
- Concatenate multiple claims for unique identifiers (e.g., `user-123:tenant-xyz`)
- Automatic fallback to IP-based limiting if JWT is missing/invalid
- No signature verification required (assumes upstream authentication)

**Example JWT Payload**:
```json
{
  "sub": "user-12345",
  "tenant_id": "acme-corp",
  "user_role": "admin"
}
```

With claims `["sub", "tenant_id"]` and separator `:`, the rate limit identifier becomes: `user-12345:acme-corp`

**See detailed documentation:** [JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)

**Test script:** `python test-jwt-rate-limit.py`

### Anti-Bot Challenge Flow

1. **Client requests form token**: `GET /api/tokens/form`
2. **Server generates token** with timestamp, stores in cache
3. **Client submits form** with:
   - `X-Form-Token` header
   - `X-Time-To-Submit` (milliseconds)
   - `X-Idempotency-Key` (optional, for duplicate prevention)
   - Honeypot field value (should be empty)
4. **Server validates**:
   - Token exists and not used
   - Submit time > configured minimum (default: 2000ms)
   - Honeypot field is empty
   - Idempotency key not seen before
5. **Accept or reject** based on validation results

### Challenge Types

Configure via Settings UI or `system_config` table:

| Type | Description | Use Case |
|------|-------------|----------|
| `metarefresh` | HTML meta tag delay | No-JS clients, accessibility |
| `javascript` | Client-side token generation | Modern web apps |
| `preact` | Lightweight React challenge | Progressive enhancement |

**Settings**:
- `antibot-challenge-type`: Challenge implementation
- `antibot-metarefresh-delay`: Delay in seconds (meta refresh only)
- `antibot-preact-difficulty`: Delay in seconds (Preact only)

## 🔌 API Reference

### Admin Endpoints

#### Rate Limit Rules
```bash
# List all rules
GET /api/admin/rules

# Get active rules only
GET /api/admin/rules/active

# Get specific rule
GET /api/admin/rules/{id}

# Create rule
POST /api/admin/rules
{
  "pathPattern": "/api/orders/**",
  "allowedRequests": 50,
  "windowSeconds": 60,
  "active": true,
  "queueEnabled": false,
  "jwtEnabled": false
}

# Create JWT-based rate limit rule
POST /api/admin/rules
{
  "pathPattern": "/api/tenant/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "active": true,
  "jwtEnabled": true,
  "jwtClaims": "[\"sub\", \"tenant_id\"]",
  "jwtClaimSeparator": ":"
}

# Update entire rule
PUT /api/admin/rules/{id}

# Update only queue settings
PATCH /api/admin/rules/{id}/queue
{
  "queueEnabled": true,
  "maxQueueSize": 5,
  "delayPerRequestMs": 300
}

# Delete rule
DELETE /api/admin/rules/{id}

# Refresh rules cache
POST /api/admin/rules/refresh
```

#### System Configuration
```bash
# Get all configs
GET /api/config

# Update specific config
POST /api/config/{key}
{
  "value": "new-value"
}
```

#### Analytics
```bash
# Get summary stats
GET /api/analytics/summary
{
  "allowed": 15234,
  "blocked": 892,
  "activePolicies": 5
}

# Get time series data
GET /api/analytics/timeseries
[
  {
    "timestamp": "2026-02-05T10:00:00Z",
    "allowed": 453,
    "blocked": 12
  }
]
```

#### Tokens & Challenges
```bash
# Get form protection token
GET /api/tokens/form
{
  "token": "uuid-here",
  "timestamp": 1739024400000
}

# Get challenge page (if configured)
GET /api/tokens/challenge
# Returns HTML with meta refresh, JS, or Preact challenge
```

### WebSocket
```javascript
// Connect to real-time analytics
const ws = new WebSocket('ws://localhost:8080/ws/analytics');
ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  console.log('Requests allowed:', update.requestsAllowed);
  console.log('Requests blocked:', update.requestsBlocked);
};
```

## 🧪 Testing

### Automated Test Suite
```bash
# Cross-platform (Python)
python run-tests.py

# Windows (PowerShell)
.\Run-Tests.ps1

# Manual
python test-server.py  # Terminal 1
python test-gateway.py # Terminal 2
```

### Test Coverage
- ✅ Gateway routing and proxying (httpbin integration)
- ✅ Rate limiting (rapid requests, burst handling)
- ✅ Queueing/leaky bucket (delay timing, queue overflow)
- ✅ Anti-bot (honeypot, timing, tokens, idempotency)
- ✅ Challenges (meta refresh, JavaScript, Preact)
- ✅ Admin API (CRUD for rules, configs)
- ✅ Analytics (summary, time series)
- ✅ WebSocket (real-time updates)

**24 automated tests** covering all features. See:
- `test-gateway.py` - Main test suite
- `AUTOMATED_QUEUEING_TESTS.md` - Queueing test details
- `QUEUEING_IMPLEMENTATION.md` - Technical implementation docs

## 📁 Architecture Overview

### Backend Structure
```
backend/src/main/java/com/example/gateway/
├── GatewayApplication.java          # Spring Boot entry point
├── config/
│   ├── DatabaseConfig.java          # R2DBC connection pool
│   ├── RateLimiterConfig.java       # Caffeine cache setup
│   └── WebSocketConfig.java         # WebSocket handler mapping
├── controller/
│   ├── AdminController.java         # Policy CRUD
│   ├── AnalyticsController.java     # Stats endpoints
│   ├── RateLimitRuleController.java # Rule management + queue config
│   ├── SystemConfigController.java  # Settings CRUD
│   └── TokenController.java         # Form tokens + challenges
├── dto/
│   ├── AnalyticsUpdate.java         # WebSocket message format
│   └── RateLimitResult.java         # Filter decision result
├── filter/
│   ├── RateLimitFilter.java         # Token bucket + queueing logic
│   └── AntiBotFilter.java           # Honeypot, timing, token validation
├── model/
│   ├── RateLimitPolicy.java         # Policy entity
│   ├── RateLimitRule.java           # Rule entity (with queue fields)
│   ├── RequestCounter.java          # Per-IP counter
│   ├── SystemConfig.java            # Config key-value store
│   └── TrafficLog.java              # Request log entry
├── ratelimit/
│   └── PostgresRateLimiter.java     # Token bucket implementation
├── repository/
│   ├── RateLimitPolicyRepository.java
│   ├── RateLimitRuleRepository.java
│   ├── RequestCounterRepository.java
│   ├── SystemConfigRepository.java
│   └── TrafficLogRepository.java
├── service/
│   ├── AnalyticsService.java        # Stats aggregation + broadcasting
│   ├── ConfigurationService.java    # Cached config access
│   ├── PolicyService.java           # Policy business logic
│   └── RateLimiterService.java      # Queue management + CAS loops
└── websocket/
    ├── AnalyticsBroadcaster.java    # Flux sink for WebSocket
    └── AnalyticsWebSocketHandler.java # WebSocket connection handler
```

### Frontend Structure
```
frontend/src/
├── main.jsx                # React entry point
├── App.jsx                 # Root component with routing
├── api.js                  # Axios client (proxied via Nginx)
├── pages/
│   ├── Dashboard.jsx       # Real-time stats with WebSocket
│   ├── Analytics.jsx       # Recharts visualizations
│   ├── Policies.jsx        # Rule CRUD interface
│   └── Settings.jsx        # System config UI
└── utils/
    ├── formProtection.js   # Anti-bot token helpers
    └── websocket.js        # WebSocket client wrapper
```

### Database Schema
```sql
-- Rate limiting
rate_limit_policies     -- Route-based policy definitions
rate_limit_rules        -- Path-based rules with queue config
rate_limit_state        -- Token bucket state (key, tokens, refill time)
request_counters        -- Per-rule, per-IP counters

-- Configuration
system_config           -- Key-value settings (cached by ConfigurationService)

-- Analytics
traffic_logs            -- Request history (timestamp, path, IP, status, allowed)
request_stats           -- Time-windowed aggregates (allowed/blocked counts)
```

## 🔒 Security Considerations

### Configuration Keys
- **`trust-x-forwarded-for`**: Set to `true` only behind trusted proxy (default: `false`)
- **`ip-header-name`**: Header for client IP extraction (default: `X-Forwarded-For`)
- **`antibot-enabled`**: Master switch for anti-bot features (default: `true`)

### CORS
- Configured at controller level via `@CrossOrigin` annotations
- Frontend proxies `/api/**` to backend via Nginx (see `frontend/nginx.conf`)

### Credentials
- Database credentials in `application.yml` and `docker-compose.yml`
- Production: Use environment variables and secrets management

## 🐛 Troubleshooting

### Docker Build Issues
```bash
# Clean rebuild
docker compose down -v
docker builder prune -af
docker compose up --build

# Check logs
docker compose logs backend | tail -50
docker compose logs frontend | tail -50
```

## 📖 Additional Documentation

- **[QUEUEING_IMPLEMENTATION.md](QUEUEING_IMPLEMENTATION.md)** - Leaky bucket technical details
- **[AUTOMATED_QUEUEING_TESTS.md](AUTOMATED_QUEUEING_TESTS.md)** - Test specifications
- **[CODEBASE_OVERVIEW.md](CODEBASE_OVERVIEW.md)** - Architectural summary
- **[TEST-README.md](TEST-README.md)** - Testing guide

## 🚢 Deployment

### Ports
| Service | Port | Description |
|---------|------|-------------|
| Frontend | 3000 | Admin UI (Nginx) |
| Backend | 8080 | API Gateway + Admin API |
| Postgres | 5432 | Database |
| Test Server | 9000 | Testing utility |

### Environment Variables
```bash
# Backend
SPRING_R2DBC_URL=r2dbc:postgresql://localhost:5432/gateway_db
SPRING_R2DBC_USERNAME=postgres
SPRING_R2DBC_PASSWORD=password
TEST_SERVER_URL=http://localhost:9000
```

### Production Checklist
- [ ] Set `trust-x-forwarded-for` based on proxy configuration
- [ ] Use secure database credentials (not `postgres/password`)
- [ ] Configure CORS for your frontend domain
- [ ] Enable HTTPS with TLS certificates
- [ ] Set up database backups and monitoring
- [ ] Tune cache sizes in `RateLimiterConfig.java`
- [ ] Configure log aggregation for `traffic_logs`
- [ ] Set up health checks and metrics

## 📄 License

Not specified - proprietary or custom license.

# Rate Limiter Queueing / Leaky Bucket Support

## Overview
Added support for queueing (leaky bucket) rate limiting that delays requests instead of immediately rejecting them when rate limits are exceeded.

## Implementation Details

### 1. Database Schema Updates (`schema.sql`)
Added three new columns to `rate_limit_rules` table:
- `queue_enabled` (BOOLEAN, default FALSE) - Enable/disable queueing for this rule
- `max_queue_size` (INT, default 0) - Maximum number of requests that can be queued
- `delay_per_request_ms` (INT, default 100) - Delay in milliseconds applied per queued position

### 2. Model Changes (`RateLimitRule.java`)
Added queue configuration fields:
```java
private boolean queueEnabled;
private int maxQueueSize;
private int delayPerRequestMs;
```

### 3. Service Layer (`RateLimiterService.java`)
**New DTO**: `RateLimitResult` - Contains:
- `boolean allowed` - Whether request is allowed
- `long delayMs` - Delay to apply (0 if immediate)
- `boolean queued` - Whether request was queued

**Queue Management**:
- Uses `ConcurrentHashMap<String, AtomicInteger>` to track queue depth per rule+IP combination
- Atomic compare-and-set (CAS) loop ensures thread-safe queue depth checking and incrementing
- Background cleanup task runs every 60 seconds to remove stale queue entries

**Queueing Logic** (`handleQueue` method):
1. Atomically check current queue depth
2. If queue is full (depth >= maxQueueSize), reject request (return `RateLimitResult(false, 0, false)`)
3. Otherwise, increment queue depth using CAS
4. Calculate delay: `position * delayPerRequestMs`
5. Schedule queue depth decrement after delay completes
6. Return `RateLimitResult(true, delayMs, true)`

### 4. Filter Layer (`RateLimitFilter.java`)
Updated to handle `RateLimitResult`:
- If request allowed but queued, adds response headers:
  - `X-RateLimit-Queued: true`
  - `X-RateLimit-Delay-Ms: {delayMs}`
- Applies delay using `Mono.delay(Duration.ofMillis(delayMs))` before proceeding

### 5. Admin API (`RateLimitRuleController.java`)
New REST controller at `/api/admin/rules` for managing rate limit rules:

**Endpoints**:
- `GET /api/admin/rules` - List all rules
- `GET /api/admin/rules/active` - List active rules only
- `GET /api/admin/rules/{id}` - Get rule by ID
- `POST /api/admin/rules` - Create new rule
- `PUT /api/admin/rules/{id}` - Update entire rule
- **`PATCH /api/admin/rules/{id}/queue`** - Update only queue settings
- `DELETE /api/admin/rules/{id}` - Delete rule
- `POST /api/admin/rules/refresh` - Manually trigger rule reload

**Queue Configuration Example**:
```json
PATCH /api/admin/rules/{id}/queue
{
  "queueEnabled": true,
  "maxQueueSize": 3,
  "delayPerRequestMs": 500
}
```

## Usage Example

### 1. Enable Queueing on a Rule
```bash
curl -X PATCH http://localhost:8080/api/admin/rules/{rule-id}/queue \
  -H "Content-Type: application/json" \
  -d '{"queueEnabled":true,"maxQueueSize":5,"delayPerRequestMs":300}'
```

### 2. Test Behavior
With a rule configured as:
- `allowedRequests`: 3
- `windowSeconds`: 10
- `queueEnabled`: true
- `maxQueueSize`: 2
- `delayPerRequestMs`: 500ms

Sending 8 rapid requests:
- **Requests 1-3**: Immediate 200 OK (within rate limit)
- **Requests 4-5**: 200 OK with 500ms and 1000ms delays (queued)
- **Requests 6-8**: 429 Too Many Requests (queue full)

### 3. Response Headers
Queued requests include:
```
X-RateLimit-Queued: true
X-RateLimit-Delay-Ms: 500
```

## Benefits

1. **Smoother Traffic Flow**: Instead of hard rejections, requests are delayed
2. **Better User Experience**: Temporary spikes don't immediately fail
3. **Configurable Per Route**: Each rate limit rule can have different queue settings
4. **Observable**: Response headers indicate queuing status and delay
5. **Thread-Safe**: Uses atomic operations for concurrent request handling

## Configuration Best Practices

- **maxQueueSize**: Set based on acceptable latency (e.g., 5-10 requests)
- **delayPerRequestMs**: Balance between smoothing traffic and response time
  - Too low: Minimal smoothing effect
  - Too high: Unacceptable latency for users
- **Example**: For bursty APIs, use `maxQueueSize=10, delayPerRequestMs=200` to handle bursts with max 2 second delay

## Technical Notes

### Race Condition Prevention
The `handleQueue` method uses a compare-and-set (CAS) loop to atomically check and increment queue depth:

```java
while (true) {
    int currentDepth = queueDepth.get();
    if (currentDepth >= rule.getMaxQueueSize()) {
        return reject();
    }
    if (queueDepth.compareAndSet(currentDepth, currentDepth + 1)) {
        position = currentDepth + 1;
        break;
    }
    // CAS failed, retry
}
```

This ensures that even under high concurrency, the queue size limit is strictly enforced.

### Queue Cleanup
A background task runs every 60 seconds to remove entries with zero depth:
```java
queueDepths.entrySet().removeIf(entry -> entry.getValue().get() <= 0);
```

This prevents memory leaks from accumulating queue tracking entries.

## Testing

See `test-queueing.py` for comprehensive tests covering:
1. Queue configuration via API
2. Basic queueing behavior
3. Delay timing accuracy
4. Concurrent request handling
5. Disabling queueing

Run tests:
```bash
python test-queueing.py
```

## Files Modified

1. `backend/src/main/java/com/example/gateway/model/RateLimitRule.java`
2. `backend/src/main/java/com/example/gateway/dto/RateLimitResult.java` (new)
3. `backend/src/main/java/com/example/gateway/service/RateLimiterService.java`
4. `backend/src/main/java/com/example/gateway/filter/RateLimitFilter.java`
5. `backend/src/main/java/com/example/gateway/controller/RateLimitRuleController.java` (new)
6. `backend/src/main/resources/schema.sql`
7. `test-queueing.py` (new)

## API Summary

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/admin/rules` | GET | List all rules |
| `/api/admin/rules/active` | GET | List active rules |
| `/api/admin/rules/{id}` | GET | Get specific rule |
| `/api/admin/rules` | POST | Create rule |
| `/api/admin/rules/{id}` | PUT | Update rule |
| `/api/admin/rules/{id}/queue` | PATCH | Update queue settings |
| `/api/admin/rules/{id}` | DELETE | Delete rule |
| `/api/admin/rules/refresh` | POST | Reload rules from DB |

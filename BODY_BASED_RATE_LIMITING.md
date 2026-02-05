# Body-Based Rate Limiting Feature

## Overview

Body-based rate limiting allows you to rate limit API requests based on JSON fields extracted from the HTTP request body. This is useful for scenarios where you want to limit requests by user ID, API key, email, tenant ID, or any other value within the JSON payload.

## Key Features

# Body-Based Rate Limiting Feature

## Overview

Body-based rate limiting lets you choose a rate-limit identifier from the HTTP request body. It supports JSON, form URL-encoded, XML, and multipart form payloads, which is useful for limiting by user ID, API key, tenant ID, or other fields in the payload.

## Key Features

- **Multi-format Extraction**: JSON, form URL-encoded, XML, and multipart form bodies
- **Multiple Limit Modes**:
  - `replace_ip`: Use body field value instead of client IP
  - `combine_with_ip`: Combine IP and body field value (`IP:value`)
- **Fallback Support**: Falls back to JWT or IP-based limiting when the body field is missing
- **Priority Chain**: Header > Cookie > Body field > JWT claims > IP address

## Configuration

### Add Body-Based Limiting to a Rule

Use the admin API on port 9090 (or via the UI) to update body settings:

```bash
curl -X PATCH http://localhost:9090/api/admin/rules/{rule-id}/body-limit \
  -H "Content-Type: application/json" \
  -d '{
    "bodyLimitEnabled": true,
    "bodyFieldPath": "user_id",
    "bodyLimitType": "replace_ip"
  }'
```

#### Body Limit Configuration DTO

```json
{
  "bodyLimitEnabled": true,
  "bodyFieldPath": "user_id",
  "bodyLimitType": "replace_ip"
}
```

Note: `bodyContentType` is stored on the rule, but the `/body-limit` PATCH endpoint does not update it. Use a full rule `POST`/`PUT` (or the UI) when you need to override the request Content-Type.

## Usage Examples

### Example 1: JSON Body (Replace IP)

```bash
curl -X POST http://localhost:8080/httpbin/post \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user123", "amount": 100}'
```

Behavior: rate limit is tracked per `user_id` value instead of IP.

### Example 2: Form URL-Encoded (Combine with IP)

```bash
curl -X POST http://localhost:8080/httpbin/post \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "api_key=abc123&amount=100"
```

Behavior: rate limit uses `IP:api_key` when `bodyLimitType` is `combine_with_ip`.

## How It Works

1. **Filter**: `RateLimitFilter` caches request bodies for POST/PUT/PATCH
2. **Rule Match**: `RateLimiterService` picks the first matching rule
3. **Field Extraction**: `BodyFieldExtractor` reads the configured field using the request Content-Type (or `bodyContentType` if set)
4. **Identifier Selection**: The identifier is chosen using the priority chain
5. **Rate Check**: Token bucket and queueing rules are applied

## Architecture

### BodyFieldExtractor Utility

Located in [util/BodyFieldExtractor.java](backend/src/main/java/com/example/gateway/util/BodyFieldExtractor.java):

- JSON dot paths: `user.id`
- Form fields: `api_key=abc123`
- XML XPath: `//user/id` or `user/id`
- Multipart form fields: simple text parts (no file parsing)

### Service Integration

`RateLimiterService` uses:

```
isAllowed(ServerWebExchange exchange, String path, String ip, String authHeader, byte[] bodyBytes)
```

The identifier is resolved in this order: Header > Cookie > Body field > JWT claims > IP.

### Request Counter Keying

- Primary key: `request_counter:<ruleId>:<identifier>`
- For `combine_with_ip`, the identifier becomes `IP:value`

## Limitations & Caveats

1. **Body is read once**: The gateway caches the request body. Large payloads increase memory usage.
2. **Content-Type mismatches**: If the request Content-Type does not match the actual payload, extraction will fail.
3. **Multipart parsing is simplified**: Only simple text fields are supported.
4. **XML namespaces**: Namespace-aware XPath is not enabled.

## Troubleshooting

### Body Field Not Being Extracted

1. Verify `bodyLimitEnabled` and `bodyFieldPath` are set
2. Ensure the Content-Type matches the actual payload, or set `bodyContentType` on the rule
3. Use dot notation for JSON, XPath for XML, and field names for form bodies
4. Enable DEBUG logs for `BodyFieldExtractor`:

```yaml
logging.level.com.example.gateway.util.BodyFieldExtractor: DEBUG
```

### Rate Limit Not Triggering

1. Check rule active: `GET /api/admin/rules/active`
2. Refresh rule cache: `POST http://localhost:9090/api/admin/rules/refresh`
3. Verify identifier selection in logs with DEBUG for `RateLimiterService`

## See Also

- [README.md](README.md)
- [JWT Rate Limiting](JWT_RATE_LIMITING.md)
- [Queueing Implementation](QUEUEING_IMPLEMENTATION.md)
## See Also

- [Rate Limiting Implementation](../README.md#rate-limiting-modes)
- [JWT Rate Limiting](JWT_RATE_LIMITING.md)
- [Queueing Implementation](QUEUEING_IMPLEMENTATION.md)

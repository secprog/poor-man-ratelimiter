# Body-Based Rate Limiting Feature

## Overview

Body-based rate limiting allows you to rate limit API requests based on JSON fields extracted from the HTTP request body. This is useful for scenarios where you want to limit requests by user ID, API key, email, tenant ID, or any other value within the JSON payload.

## Key Features

- **JSON Path Extraction**: Extract values from simple or nested JSON paths (e.g., `user_id`, `api_key`, `user.id`)
- **Multiple Limit Modes**:
  - `replace_ip`: Use body field value instead of client IP
  - `combine_with_ip`: Combine both IP and body field value for rate limiting
- **Fallback Support**: Gracefully falls back to IP-based or JWT-based limiting if body field is missing
- **Priority Chain**: Body field > JWT claims > IP address

## Configuration

### Adding Body-Based Rate Limiting to a Rule

#### Via REST API

```bash
curl -X PATCH http://localhost:8080/api/admin/rules/{rule-id}/body-limit \
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
  "bodyLimitEnabled": true,                // Enable/disable body-based limiting
  "bodyFieldPath": "user_id",               // Path to JSON field (supports dot notation)
  "bodyLimitType": "replace_ip"             // "replace_ip" or "combine_with_ip"
}
```

### Redis Storage

Body-based rule fields are stored alongside each rule in Redis under `rate_limit_rules`.

## Usage Examples

### Example 1: Rate Limit by User ID (Replace IP)

Use the admin API to update the rule with body-based settings (example above).

**Request**:
```bash
curl -X POST http://localhost:8080/api/create-order \
  -H "Content-Type: application/json" \
  -d '{"user_id": "user123", "amount": 100}'
```

**Behavior**: Rate limit will be tracked per `user_id` value instead of per IP address. Multiple users from the same IP will have separate limits.

### Example 2: Rate Limit by API Key (Combine with IP)

Use `bodyLimitType: "combine_with_ip"` and the appropriate field path in the PATCH request.

**Behavior**: Rate limit will be tracked per combination of `(IP_ADDRESS:api_key)`. Rate limit counters are separate for each IP + API key combination.

### Example 3: Rate Limit by Tenant ID (Nested Path)

```bash
curl -X PATCH http://localhost:8080/api/admin/rules/{rule-id}/body-limit \
  -H "Content-Type: application/json" \
  -d '{
    "bodyLimitEnabled": true,
    "bodyFieldPath": "organization.tenant_id",
    "bodyLimitType": "replace_ip"
  }'
```

**Request**:
```bash
curl -X POST http://localhost:8080/api/data-sync \
  -H "Content-Type: application/json" \
  -d '{
    "organization": {
      "tenant_id": "acme-corp",
      "action": "sync-now"
    }
  }'
```

**Behavior**: Rate limit tracks requests per `acme-corp` tenant ID, regardless of which user or IP makes the request.

## How It Works

### Request Flow

1. **Filter**: `RateLimitFilter` receives request
2. **Body Caching**: If POST/PUT/PATCH with JSON content-type, request body is read and cached in exchange attributes
3. **Rule Matching**: `RateLimiterService` matches request path against rate limit rules
4. **Field Extraction**: If rule has body limiting enabled, `JsonPathExtractor` extracts the specified field from cached body
5. **Identifier Determination**: `determineIdentifier()` selects the rate limiting identifier based on:
   - Body field value (if available and enabled) → highest priority
   - JWT claims (if enabled and extracted)
   - Client IP (fallback)
6. **Rate Check**: Rate limit checks are performed using the selected identifier
7. **Response**: Request is allowed, queued, or rejected based on rate limit status

### Priority Chain

When determining the rate limiting identifier:

```
IF (body-based limiting enabled AND body field found) {
    IF (combine_with_ip mode) {
        use: "IP:body_field_value"
    } ELSE {
        use: "body_field_value"  // replace_ip mode
    }
} ELSE IF (JWT-based limiting enabled AND JWT found) {
    use: "jwt_claims_combined"
} ELSE {
    use: "client_ip"  // default fallback
}
```

## Architecture

### JsonPathExtractor Utility

Located in [util/JsonPathExtractor.java](backend/src/main/java/com/example/gateway/util/JsonPathExtractor.java):

- Supports simple fields: `"user_id"` → accesses `{"user_id": "value"}`
- Supports nested paths: `"user.id"` → accesses `{"user": {"id": "value"}}`
- Returns `null` if:
  - Path doesn't exist
  - Body is not valid JSON
  - Field value is null

### Service Integration

`RateLimiterService`:
- New method: `isAllowed(String path, String ip, String authHeader, byte[] bodyBytes)`
- Returns to caller: `Mono<RateLimitResult>` (same as before)
- Handles extraction and identifier determination internally

### Request Counter Keying

Request counters are stored with:
- Primary Key: `(rule_id, client_ip)` - conceptually `client_ip` becomes "limiter_key"
- When using body field: the extracted field value is used as the key
- When combining IP + field: concatenated as `"IP:field_value"`

## Limitations & Caveats

1. **Body is read once**: The gateway reads the entire request body to extract the field. This means:
   - Applications expecting to read the body themselves may need adjustments
   - Request size limits apply (configure in Spring Cloud Gateway if needed)
   - No body re-streaming for upstream services (they receive the original body)

2. **JSON parsing errors**: If body is not valid JSON, field extraction fails silently:
   - Logs a debug message
   - Falls back to next identifier option (JWT or IP)

3. **Missing fields**: If configured field doesn't exist in JSON:
   - Logs a debug message
   - Falls back to JWT or IP-based limiting

4. **No field type conversion**: Extracted values are always strings:
   - Numeric GUIDs remain string form
   - Booleans become "true" or "false" strings
   - Objects/arrays become JSON string representation

5. **Performance**: Body extraction adds slight overhead:
   - Only for POST/PUT/PATCH requests with JSON content-type
   - Only when body-based limiting is enabled on matching rule
   - JSON parsing is cached in memory during request lifetime

## Testing

### Manual Test: Rate Limit by User ID

```bash
# Create a rule that rate limits by user_id
curl -X PATCH http://localhost:8080/api/admin/rules/{rule-id}/body-limit \
  -H "Content-Type: application/json" \
  -d '{
    "bodyLimitEnabled": true,
    "bodyFieldPath": "user_id",
    "bodyLimitType": "replace_ip"
  }'

# Make repeated requests with same user_id - should hit rate limit
for i in {1..10}; do
  curl -X POST http://localhost:8080/httpbin/post \
    -H "Content-Type: application/json" \
    -d '{"user_id":"user123","data":"test"}'
  sleep 0.1
done

# Make request with different user_id - should NOT be rate limited
curl -X POST http://localhost:8080/httpbin/post \
  -H "Content-Type: application/json" \
  -d '{"user_id":"user456","data":"test"}'
```

### Unit Test Example

```java
@Test
void testBodyFieldExtraction() {
    String json = "{\"user\": {\"id\": \"user123\"}, \"email\": \"test@example.com\"}";
    
    // Extract nested path
    String userId = JsonPathExtractor.extractField(json.getBytes(), "user.id");
    assertEquals("user123", userId);
    
    // Extract simple field
    String email = JsonPathExtractor.extractField(json.getBytes(), "email");
    assertEquals("test@example.com", email);
    
    // Non-existent path
    String notFound = JsonPathExtractor.extractField(json.getBytes(), "nonexistent.path");
    assertNull(notFound);
}
```

## Troubleshooting

### Body Field Not Being Extracted

1. **Check rule configuration**: Verify `body_limit_enabled` is true and `body_field_path` is set
2. **Check request content-type**: Must be `application/json` or contain "application/json"
3. **Check JSON structure**: Ensure the field path matches your JSON (use dot notation for nesting)
4. **Check logs**: Enable DEBUG logging for `JsonPathExtractor`:
   ```yaml
   logging.level.com.example.gateway.util.JsonPathExtractor: DEBUG
   ```

### Rate Limit Not Triggering

1. **Verify rule active**: `GET /api/admin/rules/active`
2. **Refresh rule cache**: `POST http://localhost:8080/api/admin/rules/refresh`
3. **Check identifier**: Log the determined identifier:
   ```java
   log.debug("Using identifier: {} (field: {})", identifier, field);
   ```
4. **Test with simple IP-based rule first**: Ensure rate limiting works before adding body complexity

### Requests with No Body

- If body is missing, `body_field_path` extraction returns null
- Rate limiting falls back to JWT (if enabled) then IP
- No error is logged, this is expected behavior

## Frontend Integration

In [Settings.jsx](frontend/src/pages/Settings.jsx), expose controls for:

```javascript
// Enable body-based rate limiting
<Toggle 
  label="Body Field Rate Limiting"
  value={rule.bodyLimitEnabled}
  onChange={(value) => updateRule({...rule, bodyLimitEnabled: value})}
/>

// Field path input
<Input
  label="Body Field Path"
  placeholder="e.g., user_id, api_key, user.id"
  value={rule.bodyFieldPath}
  onChange={(value) => updateRule({...rule, bodyFieldPath: value})}
  disabled={!rule.bodyLimitEnabled}
/>

// Limit type selector  
<Select
  label="Combining Mode"
  value={rule.bodyLimitType || 'replace_ip'}
  options={[
    { value: 'replace_ip', label: 'Replace IP (use body field only)' },
    { value: 'combine_with_ip', label: 'Combine with IP' }
  ]}
  onChange={(value) => updateRule({...rule, bodyLimitType: value})}
  disabled={!rule.bodyLimitEnabled}
/>
```

## See Also

- [Rate Limiting Implementation](../README.md#rate-limiting-modes)
- [JWT Rate Limiting](JWT_RATE_LIMITING.md)
- [Queueing Implementation](QUEUEING_IMPLEMENTATION.md)

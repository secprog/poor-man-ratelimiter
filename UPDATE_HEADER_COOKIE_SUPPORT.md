# Header and Cookie-based Rate Limiting

This document describes header and cookie-based rate limiting as implemented in the gateway.

## Overview

Rules can select an identifier from either a request header or cookie. You can either replace the IP entirely or combine the value with the IP (`IP:value`) for more granular keys.

Priority order when multiple modes are enabled:

Header > Cookie > Body > JWT > IP

## Configuration

Header/cookie options are part of the standard `RateLimitRule` model and are set via `POST`/`PUT` to `/api/admin/rules` (or through the Policies UI).

### Header configuration fields

- `headerLimitEnabled`: boolean
- `headerName`: header to read (e.g., `X-API-Key`)
- `headerLimitType`: `replace_ip` or `combine_with_ip`

### Cookie configuration fields

- `cookieLimitEnabled`: boolean
- `cookieName`: cookie to read (e.g., `session_id`)
- `cookieLimitType`: `replace_ip` or `combine_with_ip`

## Example

```bash
curl -X PUT http://localhost:9090/api/admin/rules/{rule-id} \
	-H "Content-Type: application/json" \
	-d '{
		"pathPattern": "/api/orders/**",
		"allowedRequests": 100,
		"windowSeconds": 60,
		"active": true,
		"headerLimitEnabled": true,
		"headerName": "X-API-Key",
		"headerLimitType": "combine_with_ip"
	}'
```

## Where It Is Used

- Backend logic: `RateLimiterService.determineIdentifier()`
- UI: Policies page supports HEADER/COOKIE types and limit type selection

## Troubleshooting

- Confirm the header/cookie is present on the request
- Ensure the rule is active and matches the request path
- Check logs with DEBUG enabled for `RateLimiterService` to see identifier selection

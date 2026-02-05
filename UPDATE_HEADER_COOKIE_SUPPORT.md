# Header and Cookie-based Rate Limiting Update

This document tracks the changes needed to add header and cookie-based rate limiting support.

## Files Modified

###  Backend Changes
1. ✅ `schema.sql` - Added header and cookie fields
2. ✅ `RateLimitRule.java` - Added header and cookie properties
3. ✅ `RateLimiterService.java` - Added header/cookie extraction logic  
4. ✅ `RateLimitFilter.java` - Pass exchange to service

### Frontend Changes (To Do)
1. `Policies.jsx` - Add header/cookie UI fields to formData state
2. `Policies.jsx` - Add header/cookie sections to modal form
3. `Policies.jsx` - Update Type column to show HEADER/COOKIE badges

## Priority Order
Header > Cookie > Body > JWT > IP

## Next Steps
Run the following commands to rebuild and deploy:
```bash
docker compose down -v
docker compose build backend --no-cache
docker compose up -d
```

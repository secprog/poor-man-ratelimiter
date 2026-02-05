# Quick Manual Test for Queueing

## Test the queueing feature manually

### 1. Get the existing rule
curl http://localhost:8080/api/admin/rules

### 2. Enable queueing on the default rule
# Replace {rule-id} with the ID from step 1 (should be 00000000-0000-0000-0000-000000000001)

curl -X PATCH http://localhost:8080/api/admin/rules/00000000-0000-0000-0000-000000000001/queue \
  -H "Content-Type: application/json" \
  -d '{"queueEnabled":true,"maxQueueSize":3,"delayPerRequestMs":500}'

### 3. Update the rule to have a tight rate limit for testing
curl -X PUT http://localhost:8080/api/admin/rules/00000000-0000-0000-0000-000000000001 \
  -H "Content-Type: application/json" \
  -d '{
    "id":"00000000-0000-0000-0000-000000000001",
    "pathPattern":"/**",
    "allowedRequests":3,
    "windowSeconds":10,
    "active":true,
    "queueEnabled":true,
    "maxQueueSize":3,
    "delayPerRequestMs":500
  }'

### 4. Send rapid requests and observe queueing
# Send 8 requests quickly - should see:
# - First 3: immediate 200 OK
# - Next 3: delayed 200 OK (with X-RateLimit-Queued header)
# - Last 2: 429 Too Many Requests

for i in {1..8}; do 
  echo "Request $i at $(date +%H:%M:%S.%N)"
  curl -i http://localhost:8080/test/api/hello 2>/dev/null | grep -E "HTTP|X-RateLimit"
done

### 5. Check response headers
# Look for:
# - X-RateLimit-Queued: true
# - X-RateLimit-Delay-Ms: {delay}

curl -i http://localhost:8080/test/api/hello | grep X-RateLimit

### 6. Disable queueing
curl -X PATCH http://localhost:8080/api/admin/rules/00000000-0000-0000-0000-000000000001/queue \
  -H "Content-Type: application/json" \
  -d '{"queueEnabled":false,"maxQueueSize":0,"delayPerRequestMs":0}'

### 7. Test again - should get immediate rejections instead of queueing
for i in {1..6}; do 
  echo "Request $i"
  curl -s -o /dev/null -w "Status: %{http_code}\n" http://localhost:8080/test/api/hello
done

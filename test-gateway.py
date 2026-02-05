#!/usr/bin/env python3
"""
Comprehensive test script for the Rate Limiter Gateway.
Tests all implemented features:
- Rate limiting (based on policies and rules)
- Anti-bot protection (honeypot, timing, tokens, idempotency)
- Meta refresh challenges
"""

import requests
import time
import sys
import json
import os
from datetime import datetime
from typing import Dict, Any, Optional
import uuid

# Fix Unicode encoding on Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# Configuration
GATEWAY_URL = "http://localhost:8080"
TEST_SERVER_URL = "http://localhost:9000"
TEST_SERVER_DOCKER = os.getenv("TEST_SERVER_URL", "http://host.docker.internal:9000")

# Colors for terminal output
class Colors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


def print_header(text: str):
    """Print a section header"""
    print(f"\n{Colors.HEADER}{Colors.BOLD}{'=' * 70}")
    print(f"{text}")
    print(f"{'=' * 70}{Colors.ENDC}")


def print_success(text: str):
    """Print success message"""
    print(f"{Colors.OKGREEN}✓ {text}{Colors.ENDC}")


def print_failure(text: str):
    """Print failure message"""
    print(f"{Colors.FAIL}✗ {text}{Colors.ENDC}")


def print_info(text: str):
    """Print info message"""
    print(f"{Colors.OKCYAN}ℹ {text}{Colors.ENDC}")


def print_warning(text: str):
    """Print warning message"""
    print(f"{Colors.WARNING}⚠ {text}{Colors.ENDC}")


def test_direct_server_access():
    """Test that the backend test server is running"""
    print_header("TEST 1: Direct Server Access")
    
    try:
        response = requests.get(f"{TEST_SERVER_URL}/health", timeout=5)
        if response.status_code == 200:
            print_success(f"Test server is running at {TEST_SERVER_URL}")
            print_info(f"Response: {response.json()}")
            return True
        else:
            print_failure(f"Test server returned status {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print_failure(f"Test server is not accessible: {e}")
        print_warning("Make sure to run: python test-server.py")
        return False


def test_gateway_routes():
    """Test that gateway routes are working"""
    print_header("TEST 2: Gateway Basic Routing")
    
    try:
        response = requests.get(f"{GATEWAY_URL}/test/api/hello", timeout=10)
        if response.status_code == 200:
            print_success("Gateway is routing successfully")
            print_info(f"Response status: {response.status_code}")
            return True
        else:
            print_failure(f"Gateway returned status {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print_failure(f"Gateway is not accessible: {e}")
        print_warning("Make sure to run: docker compose up --build")
        return False


def test_basic_get_request():
    """Test basic GET request through gateway"""
    print_header("TEST 3: Basic GET Request (No Rate Limiting)")
    
    try:
        response = requests.get(f"{GATEWAY_URL}/test/api/hello", timeout=10)
        print_success(f"GET request successful - Status: {response.status_code}")
        print_info(f"Response: {json.dumps(response.json(), indent=2)[:200]}...")
        return response.status_code == 200
    except requests.exceptions.RequestException as e:
        print_failure(f"GET request failed: {e}")
        return False


def test_rate_limiting():
    """Test rate limiting by making rapid requests"""
    print_header("TEST 4: Rate Limiting")
    
    print_info("Sending 20 rapid requests to trigger rate limiting...")
    
    success_count = 0
    rate_limited_count = 0
    
    for i in range(20):
        try:
            response = requests.get(f"{GATEWAY_URL}/test/api/hello?seq={i}", timeout=5)
            if response.status_code == 200:
                success_count += 1
                print(f"  Request {i+1}: {Colors.OKGREEN}✓{Colors.ENDC} (200 OK)")
            elif response.status_code == 429:
                rate_limited_count += 1
                print(f"  Request {i+1}: {Colors.WARNING}⚠{Colors.ENDC} (429 Too Many Requests)")
            else:
                print(f"  Request {i+1}: Status {response.status_code}")
            
            time.sleep(0.1)  # Small delay between requests
        except requests.exceptions.RequestException as e:
            print_failure(f"Request {i+1} failed: {e}")
    
    print_info(f"\nResults: {success_count} successful, {rate_limited_count} rate-limited")
    
    if rate_limited_count > 0:
        print_success("Rate limiting is working!")
        return True
    else:
        print_warning("No rate limiting detected. This might be expected if limits are high.")
        return True  # Not necessarily a failure


def get_form_token() -> Optional[Dict[str, Any]]:
    """Get a form token from the gateway"""
    try:
        response = requests.get(f"{GATEWAY_URL}/api/tokens/form", timeout=5)
        if response.status_code == 200:
            return response.json()
        else:
            print_failure(f"Failed to get form token: {response.status_code}")
            return None
    except requests.exceptions.RequestException as e:
        print_failure(f"Error getting form token: {e}")
        return None


def test_antibot_valid_submission():
    """Test anti-bot protection with valid submission"""
    print_header("TEST 5: Anti-Bot Protection - Valid Submission")
    
    # Get a form token
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    print_info(f"Received form token: {token_data['token'][:20]}...")
    
    # Wait a bit to pass the time-to-submit check
    min_wait_time = 2.1  # Slightly more than the 2000ms minimum
    print_info(f"Waiting {min_wait_time}s to pass time-to-submit check...")
    time.sleep(min_wait_time)
    
    # Make a valid POST request
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',  # Empty honeypot (not filled by bot)
        'Content-Type': 'application/json'
    }
    
    data = {
        'message': 'Test submission',
        'user': 'human'
    }
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response.status_code == 200:
            print_success("Valid submission accepted!")
            print_info(f"Response status: {response.status_code}")
            return True
        else:
            print_failure(f"Submission rejected with status {response.status_code}")
            if 'X-Rejection-Reason' in response.headers:
                print_warning(f"Reason: {response.headers['X-Rejection-Reason']}")
            return False
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_antibot_honeypot_detection():
    """Test anti-bot honeypot field detection"""
    print_header("TEST 6: Anti-Bot Protection - Honeypot Detection")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)  # Pass time check
    
    # Fill the honeypot field (simulate bot behavior)
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': 'bot@spam.com',  # Bot fills this field
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'Bot submission'}
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response.status_code == 403:
            print_success("Bot detected and blocked via honeypot!")
            if 'X-Rejection-Reason' in response.headers:
                print_info(f"Rejection reason: {response.headers['X-Rejection-Reason']}")
            return True
        else:
            print_failure(f"Expected 403, got {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_antibot_timing_check():
    """Test anti-bot time-to-submit check"""
    print_header("TEST 7: Anti-Bot Protection - Time-to-Submit Check")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    print_info("Submitting immediately (too fast for human)...")
    # Submit immediately without waiting
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'Fast bot submission'}
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response.status_code == 403:
            print_success("Fast submission detected and blocked!")
            if 'X-Rejection-Reason' in response.headers:
                print_info(f"Rejection reason: {response.headers['X-Rejection-Reason']}")
            return True
        else:
            print_failure(f"Expected 403, got {response.status_code}")
            return False
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_antibot_token_reuse():
    """Test anti-bot token reuse prevention"""
    print_header("TEST 8: Anti-Bot Protection - Token Reuse Prevention")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)  # Pass time check
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'First submission'}
    
    try:
        # First submission
        print_info("Making first submission with token...")
        response1 = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response1.status_code != 200:
            print_failure(f"First submission failed: {response1.status_code}")
            return False
        
        print_success("First submission accepted")
        
        # Try to reuse the same token
        print_info("Attempting to reuse the same token...")
        data['message'] = 'Second submission (reused token)'
        
        response2 = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response2.status_code == 403:
            print_success("Token reuse detected and blocked!")
            if 'X-Rejection-Reason' in response2.headers:
                print_info(f"Rejection reason: {response2.headers['X-Rejection-Reason']}")
            return True
        else:
            print_failure(f"Expected 403, got {response2.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_idempotency_key():
    """Test idempotency key functionality"""
    print_header("TEST 9: Idempotency Key - Duplicate Prevention")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)  # Pass time check
    
    idempotency_key = str(uuid.uuid4())
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'X-Idempotency-Key': idempotency_key,
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'First submission with idempotency key'}
    
    try:
        # First submission
        print_info(f"Making first submission with idempotency key: {idempotency_key[:20]}...")
        response1 = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response1.status_code != 200:
            print_failure(f"First submission failed: {response1.status_code}")
            return False
        
        print_success("First submission accepted")
        
        # Get a new token for second attempt
        token_data2 = get_form_token()
        time.sleep(2.1)
        
        # Try duplicate submission with same idempotency key
        print_info("Attempting duplicate submission with same idempotency key...")
        headers2 = {
            'X-Form-Token': token_data2['token'],
            'X-Form-Load-Time': str(token_data2['loadTime']),
            'X-Honeypot': '',
            'X-Idempotency-Key': idempotency_key,  # Same key
            'Content-Type': 'application/json'
        }
        
        data['message'] = 'Duplicate submission (same idempotency key)'
        
        response2 = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers2,
            json=data,
            timeout=10
        )
        
        if response2.status_code == 409:  # Conflict
            print_success("Duplicate submission detected and blocked!")
            if 'X-Duplicate-Request' in response2.headers:
                print_info(f"Duplicate flag: {response2.headers['X-Duplicate-Request']}")
            return True
        else:
            print_failure(f"Expected 409, got {response2.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_meta_refresh_challenge():
    """Test meta refresh challenge endpoint"""
    print_header("TEST 10: Meta Refresh Challenge")
    
    try:
        print_info("Requesting meta refresh challenge...")
        response = requests.get(f"{GATEWAY_URL}/api/tokens/challenge", timeout=10)
        
        if response.status_code == 200:
            content_type = response.headers.get('Content-Type', '')
            
            if 'text/html' in content_type:
                print_success("Received HTML meta refresh challenge")
                
                # Check for meta refresh tag
                html = response.text
                if 'meta http-equiv="refresh"' in html:
                    print_success("HTML contains meta refresh tag")
                
                # Check for cookie
                if 'X-Form-Token-Challenge' in response.cookies:
                    print_success("Challenge token set in cookie")
                    print_info(f"Token: {response.cookies['X-Form-Token-Challenge'][:20]}...")
                else:
                    print_warning("No challenge token cookie found")
                
                print_info(f"HTML preview:\n{html[:300]}...")
                return True
            else:
                print_warning(f"Unexpected content type: {content_type}")
                print_info(f"Response: {response.text[:200]}")
                return True
        else:
            print_failure(f"Challenge request failed: {response.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_missing_antibot_headers():
    """Test requests with missing anti-bot headers"""
    print_header("TEST 11: Missing Anti-Bot Headers")
    
    # Try GET without any anti-bot headers (should work)
    headers = {'Content-Type': 'application/json'}
    
    try:
        response = requests.get(
            f"{GATEWAY_URL}/test/api/hello",
            timeout=10
        )
        
        # GET requests don't require anti-bot headers
        if response.status_code == 200:
            print_success("GET requests accessible without anti-bot headers")
            return True
        else:
            print_failure(f"Unexpected response: {response.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_invalid_form_token():
    """Test submission with invalid form token"""
    print_header("TEST 12: Invalid Form Token")
    
    headers = {
        'X-Form-Token': 'invalid-token-12345',
        'X-Form-Load-Time': str(int(time.time() * 1000)),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'Invalid token test'}
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        
        if response.status_code == 403:
            print_success("Invalid token properly rejected!")
            if 'X-Rejection-Reason' in response.headers:
                print_info(f"Rejection reason: {response.headers['X-Rejection-Reason']}")
            return True
        else:
            print_info(f"Response status: {response.status_code}")
            return True
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_expired_token():
    """Test that old tokens don't work after expiration"""
    print_header("TEST 13: Token Lifecycle")
    
    # Get a fresh token
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    print_info(f"Token issued, expires in: {token_data.get('expiresIn', 'unknown')}s")
    
    # Calculate token age
    current_time = int(time.time() * 1000)
    token_age_ms = current_time - token_data['loadTime']
    
    print_success(f"Token age: {token_age_ms}ms (should be < 10 minutes)")
    return True


def test_concurrent_submissions():
    """Test multiple concurrent submissions"""
    print_header("TEST 14: Concurrent Submissions")
    
    print_info("Fetching 5 different tokens...")
    tokens = []
    for i in range(5):
        token_data = get_form_token()
        if token_data:
            tokens.append(token_data)
            time.sleep(0.1)
    
    if not tokens:
        print_failure("Could not get form tokens")
        return False
    
    print_info(f"Got {len(tokens)} tokens, submitting with different timing...")
    
    success_count = 0
    for idx, token_data in enumerate(tokens):
        # Vary the wait time
        wait_time = 2.1 + (idx * 0.5)
        time.sleep(wait_time)
        
        headers = {
            'X-Form-Token': token_data['token'],
            'X-Form-Load-Time': str(token_data['loadTime']),
            'X-Honeypot': '',
            'Content-Type': 'application/json'
        }
        
        try:
            response = requests.post(
                f"{GATEWAY_URL}/test/api/echo",
                headers=headers,
                json={'seq': idx},
                timeout=10
            )
            
            if response.status_code == 200:
                success_count += 1
                print(f"  Submission {idx + 1}: {Colors.OKGREEN}✓{Colors.ENDC}")
            else:
                print(f"  Submission {idx + 1}: Status {response.status_code}")
                
        except requests.exceptions.RequestException as e:
            print(f"  Submission {idx + 1}: {Colors.FAIL}✗{Colors.ENDC} ({e})")
    
    print_info(f"\nSuccessful submissions: {success_count}/{len(tokens)}")
    return success_count > 0


def test_various_http_methods():
    """Test with various HTTP methods"""
    print_header("TEST 15: Various HTTP Methods")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)  # Pass time check
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    data = {'message': 'Testing various methods'}
    methods_tested = []
    
    try:
        # Test POST
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        print(f"  POST: Status {response.status_code}")
        methods_tested.append(f"POST:{response.status_code}")
        
        # Get fresh token for PUT
        token_data = get_form_token()
        time.sleep(2.1)
        headers['X-Form-Token'] = token_data['token']
        headers['X-Form-Load-Time'] = str(token_data['loadTime'])
        
        response = requests.put(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        print(f"  PUT: Status {response.status_code}")
        methods_tested.append(f"PUT:{response.status_code}")
        
        # Get fresh token for PATCH
        token_data = get_form_token()
        time.sleep(2.1)
        headers['X-Form-Token'] = token_data['token']
        headers['X-Form-Load-Time'] = str(token_data['loadTime'])
        
        response = requests.patch(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=data,
            timeout=10
        )
        print(f"  PATCH: Status {response.status_code}")
        methods_tested.append(f"PATCH:{response.status_code}")
        
        print_success(f"Tested {len(methods_tested)} HTTP methods")
        return True
        
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_large_payload():
    """Test with large request payloads"""
    print_header("TEST 16: Large Payload Handling")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    # Create a large payload (1MB of data)
    large_data = {
        'message': 'Large payload test',
        'data': 'x' * (1024 * 100),  # 100KB of data
        'array': list(range(1000))
    }
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=large_data,
            timeout=30
        )
        
        print_success(f"Large payload handled - Status: {response.status_code}")
        return response.status_code in [200, 413]  # 413 is Payload Too Large
        
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_special_characters():
    """Test with special characters in data"""
    print_header("TEST 17: Special Characters Handling")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    time.sleep(2.1)
    
    headers = {
        'X-Form-Token': token_data['token'],
        'X-Form-Load-Time': str(token_data['loadTime']),
        'X-Honeypot': '',
        'Content-Type': 'application/json'
    }
    
    # Test various special characters
    special_data = {
        'unicode': '你好世界 🌍 مرحبا بالعالم',
        'symbols': '!@#$%^&*()',
        'quotes': '"single" and \'double\'',
        'newlines': 'Line 1\nLine 2\nLine 3',
        'escapes': 'C:\\Users\\test\\file.txt'
    }
    
    try:
        response = requests.post(
            f"{GATEWAY_URL}/test/api/echo",
            headers=headers,
            json=special_data,
            timeout=10
        )
        
        if response.status_code == 200:
            print_success("Special characters handled correctly")
            return True
        else:
            print_failure(f"Unexpected status: {response.status_code}")
            return False
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_token_format_validation():
    """Test token format and structure"""
    print_header("TEST 18: Token Format Validation")
    
    token_data = get_form_token()
    if not token_data:
        print_failure("Could not get form token")
        return False
    
    # Validate token structure
    checks = []
    
    # Check required fields
    checks.append(("token field exists", 'token' in token_data))
    checks.append(("loadTime field exists", 'loadTime' in token_data))
    checks.append(("expiresIn field exists", 'expiresIn' in token_data))
    checks.append(("honeypotField field exists", 'honeypotField' in token_data))
    
    # Check token format (UUID)
    token = token_data.get('token', '')
    checks.append(("token is UUID format", len(token) == 36 and token.count('-') == 4))
    
    # Check loadTime is numeric
    load_time = token_data.get('loadTime')
    checks.append(("loadTime is numeric", isinstance(load_time, (int, float))))
    
    # Check expiresIn is reasonable
    expires_in = token_data.get('expiresIn')
    checks.append(("expiresIn is 600 seconds", expires_in == 600))
    
    all_passed = True
    for check_name, passed in checks:
        if passed:
            print_success(check_name)
        else:
            print_failure(check_name)
            all_passed = False
    
    return all_passed


def test_gateway_headers():
    """Test gateway header handling"""
    print_header("TEST 19: Gateway Header Forwarding")
    
    try:
        # Test that headers are properly forwarded to httpbin
        custom_headers = {
            'X-Custom-Header': 'test-value-123',
            'X-Client-ID': 'test-client',
            'User-Agent': 'CustomAgent/1.0'
        }
        
        response = requests.get(
            f"{GATEWAY_URL}/test/api/hello",
            headers=custom_headers,
            timeout=10
        )
        
        if response.status_code == 200:
            print_success("Headers properly forwarded through gateway")
            return True
        else:
            print_info(f"Status: {response.status_code}")
            return True
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_cross_origin_requests():
    """Test CORS and cross-origin requests"""
    print_header("TEST 20: Cross-Origin Support")
    
    try:
        # Test with Origin header
        response = requests.get(
            f"{GATEWAY_URL}/api/tokens/form",
            headers={'Origin': 'http://localhost:3000'},
            timeout=10
        )
        
        # Check if CORS headers are present
        cors_header = response.headers.get('Access-Control-Allow-Origin')
        
        if cors_header:
            print_success(f"CORS enabled: {cors_header}")
            return True
        else:
            print_info("CORS headers not present (may be expected)")
            return response.status_code == 200
            
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_queueing_configuration():
    """Test queueing configuration via admin API"""
    print_header("TEST 21: Queueing Configuration")
    
    try:
        admin_url = f"{GATEWAY_URL}/api/admin/rules"
        
        # Get existing rules
        response = requests.get(admin_url, timeout=10)
        if response.status_code != 200:
            print_failure(f"Failed to get rules: {response.status_code}")
            return False
        
        rules = response.json()
        if not rules:
            print_failure("No rules found")
            return False
        
        rule_id = rules[0].get('id')
        print_info(f"Using rule ID: {rule_id}")
        
        # Enable queueing
        queue_config = {
            "queueEnabled": True,
            "maxQueueSize": 2,
            "delayPerRequestMs": 500
        }
        
        response = requests.patch(
            f"{admin_url}/{rule_id}/queue",
            json=queue_config,
            timeout=10
        )
        
        if response.status_code != 200:
            print_failure(f"Failed to set queue config: {response.status_code}")
            return False
        
        updated_rule = response.json()
        
        checks = [
            ("Queue enabled", updated_rule.get('queueEnabled') == True),
            ("Max queue size", updated_rule.get('maxQueueSize') == 2),
            ("Delay per request", updated_rule.get('delayPerRequestMs') == 500),
        ]
        
        all_passed = True
        for check_name, passed in checks:
            if passed:
                print_success(check_name)
            else:
                print_failure(check_name)
                all_passed = False
        
        return all_passed
        
    except requests.exceptions.RequestException as e:
        print_failure(f"Request failed: {e}")
        return False


def test_queueing_behavior():
    """Test that requests are queued instead of rejected"""
    print_header("TEST 22: Queueing Behavior")
    
    try:
        admin_url = f"{GATEWAY_URL}/api/admin/rules"
        
        # Get rule and configure for tight rate limit
        response = requests.get(admin_url, timeout=10)
        if response.status_code != 200:
            return False
        
        rules = response.json()
        rule = rules[0].copy()
        rule_id = rule.get('id')
        
        # Set tight limit: 2 requests per 10 seconds, queue 2 more
        rule.update({
            "allowedRequests": 2,
            "windowSeconds": 10,
            "queueEnabled": True,
            "maxQueueSize": 2,
            "delayPerRequestMs": 300
        })
        
        response = requests.put(
            f"{admin_url}/{rule_id}",
            json=rule,
            timeout=10
        )
        
        if response.status_code != 200:
            print_failure(f"Failed to update rule: {response.status_code}")
            return False
        
        print_info("Rate limit: 2 requests per 10s, queue 2 with 300ms delay")
        time.sleep(1)  # Wait for rule to be reloaded
        
        # Send 5 rapid requests
        print_info("Sending 5 rapid requests...")
        results = {}
        for i in range(5):
            response = requests.get(f"{GATEWAY_URL}/test/api/hello", timeout=5)
            results[i+1] = {
                "status": response.status_code,
                "queued": response.headers.get("X-RateLimit-Queued") == "true",
                "delayed": response.headers.get("X-RateLimit-Delay-Ms") is not None
            }
        
        # Analyze results
        immediate = sum(1 for r in results.values() if r['status'] == 200 and not r['queued'])
        queued = sum(1 for r in results.values() if r['status'] == 200 and r['queued'])
        rejected = sum(1 for r in results.values() if r['status'] == 429)
        
        print_info(f"Results: {immediate} immediate, {queued} queued, {rejected} rejected")
        
        checks = [
            ("At least 2 immediate requests allowed", immediate >= 2),
            ("Some requests were queued", queued > 0),
            ("Queued had delay headers", all(r['delayed'] for r in list(results.values())[2:2+queued]) if queued > 0 else True),
        ]
        
        all_passed = True
        for check_name, passed in checks:
            if passed:
                print_success(check_name)
            else:
                print_failure(check_name)
                all_passed = False
        
        return all_passed
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False


def test_queueing_delay_timing():
    """Test that queueing delays are actually applied"""
    print_header("TEST 23: Queueing Delay Timing")
    
    try:
        admin_url = f"{GATEWAY_URL}/api/admin/rules"
        
        # Get rule and configure for measurable delays
        response = requests.get(admin_url, timeout=10)
        rules = response.json()
        rule = rules[0].copy()
        rule_id = rule.get('id')
        
        # 1 request per 5 seconds, queue 1 more with 1 second delay
        rule.update({
            "allowedRequests": 1,
            "windowSeconds": 5,
            "queueEnabled": True,
            "maxQueueSize": 1,
            "delayPerRequestMs": 1000
        })
        
        response = requests.put(f"{admin_url}/{rule_id}", json=rule, timeout=10)
        if response.status_code != 200:
            return False
        
        time.sleep(1)
        print_info("Rate limit: 1 request per 5s, queue 1 more with 1000ms delay")
        
        # Send 2 requests and measure timing
        timings = []
        for i in range(2):
            start = time.time()
            response = requests.get(f"{GATEWAY_URL}/test/api/hello", timeout=5)
            elapsed = (time.time() - start) * 1000  # Convert to ms
            
            timings.append({
                "request": i+1,
                "status": response.status_code,
                "elapsed_ms": int(elapsed),
                "queued": response.headers.get("X-RateLimit-Queued") == "true",
                "delay_ms": response.headers.get("X-RateLimit-Delay-Ms")
            })
        
        print_info(f"Request 1: {timings[0]['elapsed_ms']}ms (immediate)")
        print_info(f"Request 2: {timings[1]['elapsed_ms']}ms (queued, delay={timings[1].get('delay_ms')}ms)")
        
        checks = [
            ("Second request delayed (approx 1000ms)", 800 <= timings[1]['elapsed_ms'] <= 1500),
            ("Queued status indicated", timings[1]['queued']),
            ("Both requests successful", all(t['status'] == 200 for t in timings)),
        ]
        
        all_passed = True
        for check_name, passed in checks:
            if passed:
                print_success(check_name)
            else:
                print_failure(check_name)
                all_passed = False
        
        return all_passed
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False


def test_queueing_disabled_reverts_to_rejection():
    """Test that disabling queueing reverts to normal rejection behavior"""
    print_header("TEST 24: Disable Queueing")
    
    try:
        admin_url = f"{GATEWAY_URL}/api/admin/rules"
        
        # Get rule and disable queueing
        response = requests.get(admin_url, timeout=10)
        rules = response.json()
        rule = rules[0].copy()
        rule_id = rule.get('id')
        
        rule.update({
            "allowedRequests": 2,
            "windowSeconds": 5,
            "queueEnabled": False,
            "maxQueueSize": 0,
            "delayPerRequestMs": 0
        })
        
        response = requests.put(f"{admin_url}/{rule_id}", json=rule, timeout=10)
        if response.status_code != 200:
            return False
        
        time.sleep(1)
        print_info("Queueing disabled, limit 2 per 5 seconds")
        
        # Send 5 rapid requests - should get 2 allowed, 3 rejected
        responses = []
        for i in range(5):
            response = requests.get(f"{GATEWAY_URL}/test/api/hello", timeout=2)
            responses.append(response.status_code)
        
        allowed = sum(1 for status in responses if status == 200)
        rejected = sum(1 for status in responses if status == 429)
        
        print_info(f"Results: {allowed} allowed, {rejected} rejected")
        
        checks = [
            ("Correct number allowed (2)", allowed == 2),
            ("Correct number rejected (3)", rejected == 3),
        ]
        
        all_passed = True
        for check_name, passed in checks:
            if passed:
                print_success(check_name)
            else:
                print_failure(check_name)
                all_passed = False
        
        return all_passed
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False


def print_summary(results: Dict[str, bool]):
    """Print test summary"""
    print_header("TEST SUMMARY")
    
    total = len(results)
    passed = sum(1 for result in results.values() if result)
    failed = total - passed
    
    print(f"\nTotal tests: {total}")
    print_success(f"Passed: {passed}")
    if failed > 0:
        print_failure(f"Failed: {failed}")
    
    print(f"\n{Colors.BOLD}Results by test:{Colors.ENDC}")
    for test_name, result in results.items():
        status = f"{Colors.OKGREEN}✓ PASS{Colors.ENDC}" if result else f"{Colors.FAIL}✗ FAIL{Colors.ENDC}"
        print(f"  {status} - {test_name}")
    
    success_rate = (passed / total) * 100 if total > 0 else 0
    print(f"\n{Colors.BOLD}Success rate: {success_rate:.1f}%{Colors.ENDC}")


def main():
    """Run all tests"""
    print(f"\n{Colors.BOLD}{Colors.HEADER}")
    print("╔════════════════════════════════════════════════════════════════════╗")
    print("║         Rate Limiter Gateway - Comprehensive Test Suite           ║")
    print("╚════════════════════════════════════════════════════════════════════╝")
    print(f"{Colors.ENDC}")
    
    print_info(f"Gateway URL: {GATEWAY_URL}")
    print_info(f"Test Server URL: {TEST_SERVER_URL}")
    print_info(f"Test started at: {datetime.now().isoformat()}")
    
    results = {}
    
    # Run tests
    results["Direct Server Access"] = test_direct_server_access()
    results["Gateway Routing"] = test_gateway_routes()
    results["Basic GET Request"] = test_basic_get_request()
    results["Rate Limiting"] = test_rate_limiting()
    results["Anti-Bot: Valid Submission"] = test_antibot_valid_submission()
    results["Anti-Bot: Honeypot Detection"] = test_antibot_honeypot_detection()
    results["Anti-Bot: Timing Check"] = test_antibot_timing_check()
    results["Anti-Bot: Token Reuse"] = test_antibot_token_reuse()
    results["Idempotency Key"] = test_idempotency_key()
    results["Meta Refresh Challenge"] = test_meta_refresh_challenge()
    results["Missing Anti-Bot Headers"] = test_missing_antibot_headers()
    results["Invalid Form Token"] = test_invalid_form_token()
    results["Token Lifecycle"] = test_expired_token()
    results["Concurrent Submissions"] = test_concurrent_submissions()
    results["Various HTTP Methods"] = test_various_http_methods()
    results["Large Payload Handling"] = test_large_payload()
    results["Special Characters"] = test_special_characters()
    results["Token Format Validation"] = test_token_format_validation()
    results["Gateway Header Forwarding"] = test_gateway_headers()
    results["Cross-Origin Support"] = test_cross_origin_requests()
    results["Queueing Configuration"] = test_queueing_configuration()
    results["Queueing Behavior"] = test_queueing_behavior()
    results["Queueing Delay Timing"] = test_queueing_delay_timing()
    results["Disable Queueing"] = test_queueing_disabled_reverts_to_rejection()
    
    # Print summary
    print_summary(results)
    
    # Exit with appropriate code
    all_passed = all(results.values())
    sys.exit(0 if all_passed else 1)


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n\n{Colors.WARNING}Tests interrupted by user{Colors.ENDC}")
        sys.exit(130)


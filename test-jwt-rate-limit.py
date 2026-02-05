"""
JWT Rate Limiting Test Script

This script tests the JWT-based rate limiting feature by:
1. Creating a test rate limit rule with JWT enabled
2. Generating test JWT tokens with different claims
3. Sending requests with various tokens to verify rate limiting
4. Comparing JWT-based vs IP-based rate limiting behavior
"""

import requests
import json
import time
import base64
from typing import Dict, Any

# Configuration
GATEWAY_BASE_URL = "http://localhost:8080"
ADMIN_API_URL = f"http://localhost:9090/poormansRateLimit/api/admin/rules"
TEST_ENDPOINT = f"{GATEWAY_BASE_URL}/get"  # Direct path to httpbin's /get endpoint
HTTPBIN_TARGET_URL = "http://httpbin:80/get"  # Full httpbin URL with path


def create_unsecured_jwt(payload: Dict[str, Any]) -> str:
    """
    Create an unsecured JWT (no signature) for testing purposes.
    The gateway doesn't validate signatures, so this is sufficient.

    Format: base64(header).base64(payload).
    """
    header = {"alg": "none", "typ": "JWT"}

    # Encode header and payload
    header_b64 = (
        base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    )

    payload_b64 = (
        base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    )

    # Unsecured JWT format: header.payload.
    return f"{header_b64}.{payload_b64}."


def create_jwt_rule():
    """Create a JWT-based rate limit rule for testing."""
    rule = {
        "pathPattern": "/get",  # Match httpbin's /get endpoint directly
        "targetUri": HTTPBIN_TARGET_URL,
        "allowedRequests": 5,  # Low limit for easy testing
        "windowSeconds": 60,
        "active": True,
        "priority": -1,
        "queueEnabled": False,
        "jwtEnabled": True,
        "jwtClaims": json.dumps(["sub", "tenant_id"]),
        "jwtClaimSeparator": ":",
    }

    try:
        response = requests.post(ADMIN_API_URL, json=rule)
        response.raise_for_status()
        print(f"[OK] Created JWT rate limit rule: {response.json()['id']}")
        return response.json()
    except Exception as e:
        print(f"[FAIL] Failed to create rule: {e}")
        return None


def test_jwt_rate_limiting():
    """Test JWT-based rate limiting with different token combinations."""

    # Test Case 1: User A from Tenant X
    print("\n--- Test Case 1: User A from Tenant X (5 req limit) ---")
    token_a_x = create_unsecured_jwt(
        {"sub": "user-a", "tenant_id": "tenant-x", "iat": int(time.time())}
    )

    headers_a_x = {"Authorization": f"Bearer {token_a_x}"}

    for i in range(7):
        response = requests.get(TEST_ENDPOINT, headers=headers_a_x)
        status_icon = "[OK]" if response.status_code == 200 else "[BLOCKED]"
        print(f"  Request {i+1}: {status_icon} {response.status_code}")
        time.sleep(0.1)

    # Test Case 2: User B from Tenant X (different user, same tenant)
    print("\n--- Test Case 2: User B from Tenant X (separate quota) ---")
    token_b_x = create_unsecured_jwt(
        {"sub": "user-b", "tenant_id": "tenant-x", "iat": int(time.time())}
    )

    headers_b_x = {"Authorization": f"Bearer {token_b_x}"}

    for i in range(3):
        response = requests.get(TEST_ENDPOINT, headers=headers_b_x)
        status_icon = "[OK]" if response.status_code == 200 else "[BLOCKED]"
        print(f"  Request {i+1}: {status_icon} {response.status_code}")
        time.sleep(0.1)

    # Test Case 3: User A from Tenant Y (same user, different tenant)
    print("\n--- Test Case 3: User A from Tenant Y (separate quota) ---")
    token_a_y = create_unsecured_jwt(
        {"sub": "user-a", "tenant_id": "tenant-y", "iat": int(time.time())}
    )

    headers_a_y = {"Authorization": f"Bearer {token_a_y}"}

    for i in range(3):
        response = requests.get(TEST_ENDPOINT, headers=headers_a_y)
        status_icon = "[OK]" if response.status_code == 200 else "[BLOCKED]"
        print(f"  Request {i+1}: {status_icon} {response.status_code}")
        time.sleep(0.1)

    # Test Case 4: No JWT token (fallback to IP)
    print("\n--- Test Case 4: No JWT token (IP-based fallback) ---")

    for i in range(3):
        response = requests.get(TEST_ENDPOINT)
        status_icon = "[OK]" if response.status_code == 200 else "[BLOCKED]"
        print(f"  Request {i+1}: {status_icon} {response.status_code}")
        time.sleep(0.1)

    # Test Case 5: Invalid JWT token (fallback to IP)
    print("\n--- Test Case 5: Invalid JWT token (IP-based fallback) ---")

    headers_invalid = {"Authorization": "Bearer invalid.token.here"}

    for i in range(3):
        response = requests.get(TEST_ENDPOINT, headers=headers_invalid)
        status_icon = "[OK]" if response.status_code == 200 else "[BLOCKED]"
        print(f"  Request {i+1}: {status_icon} {response.status_code}")
        time.sleep(0.1)


def cleanup_rules():
    """Delete all test rules."""
    try:
        response = requests.get(ADMIN_API_URL)
        rules = response.json()

        for rule in rules:
            if rule["pathPattern"] == "/get":
                delete_url = f"{ADMIN_API_URL}/{rule['id']}"
                requests.delete(delete_url)
                print(f"[OK] Deleted test rule: {rule['id']}")
    except Exception as e:
        print(f"[WARN] Cleanup error: {e}")


def main():
    print("=" * 70)
    print("JWT Rate Limiting Test")
    print("=" * 70)

    print("\n[*] Step 1: Cleaning up any existing test rules...")
    cleanup_rules()

    print("\n[*] Step 2: Creating JWT-based rate limit rule...")
    rule = create_jwt_rule()

    if not rule:
        print("[!] Cannot proceed without a valid rule. Exiting.")
        return

    print("\n[...] Waiting 2 seconds for rule to activate...")
    time.sleep(2)

    print("\n[*] Step 3: Running JWT rate limit tests...")
    test_jwt_rate_limiting()

    print("\n[*] Step 4: Cleaning up test rules...")
    cleanup_rules()

    print("\n" + "=" * 70)
    print("[OK] Test complete!")
    print("=" * 70)

    print("\n[!] Key Observations:")
    print("  • Different JWT identifiers (sub:tenant_id) have separate quotas")
    print("  • Same user in different tenants = different quotas")
    print("  • Missing/invalid JWT → automatic fallback to IP-based limiting")
    print("  • Expected: 5 successful requests per identifier, then 429")


if __name__ == "__main__":
    main()

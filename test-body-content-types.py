#!/usr/bin/env python3
"""
Test script for multi-content-type body field extraction in rate limiting.
Tests JSON, Form Data, XML, and Multipart Form Data.
"""

import requests
import json
import time

BASE_URL = "http://localhost:8080"
API_URL = f"{BASE_URL}/api/admin/rules"
TEST_PATH = "/test/api/echo"

def create_body_rate_limit_rule(field_path, content_type):
    """Create a rate limit rule for body-based limiting with specified content type."""
    rule = {
        "pathPattern": "/test/**",
        "allowedRequests": 5,
        "windowSeconds": 60,
        "active": True,
        "priority": -1,
        "queueEnabled": False,
        "maxQueueSize": 10,
        "delayPerRequestMs": 500,
        "jwtEnabled": False,
        "headerLimitEnabled": False,
        "cookieLimitEnabled": False,
        "bodyLimitEnabled": True,
        "bodyFieldPath": field_path,
        "bodyLimitType": "replace_ip",
        "bodyContentType": content_type
    }
    
    response = requests.post(API_URL, json=rule)
    print(f"Created rule with content type '{content_type}': {response.status_code}")
    return response.json() if response.status_code in [200, 201] else None

def test_json_body():
    """Test JSON body field extraction."""
    print("\n=== Testing JSON Body Field Extraction ===")
    
    # Create rule for JSON
    rule = create_body_rate_limit_rule("user_id", "application/json")
    
    # Test requests with different user_id values
    users = ["user1", "user2", "user1"]
    
    for user in users:
        response = requests.post(
            f"{BASE_URL}{TEST_PATH}",
            headers={"Content-Type": "application/json"},
            json={"user_id": user, "action": "test"},
            timeout=10
        )
        print(f"  Request with user_id={user}: {response.status_code}")
        time.sleep(0.5)
    
    # Clean up
    if rule and 'id' in rule:
        requests.delete(f"{API_URL}/{rule['id']}")

def test_form_data():
    """Test URL-encoded form data field extraction."""
    print("\n=== Testing Form Data Field Extraction ===")
    
    # Create rule for form data
    rule = create_body_rate_limit_rule("api_key", "application/x-www-form-urlencoded")
    
    # Test requests with different api_key values
    api_keys = ["key123", "key456", "key123"]
    
    for key in api_keys:
        response = requests.post(
            f"{BASE_URL}{TEST_PATH}",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            data={"api_key": key, "action": "test"},
            timeout=10
        )
        print(f"  Request with api_key={key}: {response.status_code}")
        time.sleep(0.5)
    
    # Clean up
    if rule and 'id' in rule:
        requests.delete(f"{API_URL}/{rule['id']}")

def test_xml_body():
    """Test XML body field extraction."""
    print("\n=== Testing XML Body Field Extraction ===")
    
    # Create rule for XML (using XPath)
    rule = create_body_rate_limit_rule("//client/id", "application/xml")
    
    # Test requests with different client IDs
    client_ids = ["client1", "client2", "client1"]
    
    for client_id in client_ids:
        xml_body = f"""<?xml version="1.0" encoding="UTF-8"?>
<request>
    <client>
        <id>{client_id}</id>
        <name>Test Client</name>
    </client>
    <action>test</action>
</request>"""
        
        response = requests.post(
            f"{BASE_URL}{TEST_PATH}",
            headers={"Content-Type": "application/xml"},
            data=xml_body.encode('utf-8'),
            timeout=10
        )
        print(f"  Request with client_id={client_id}: {response.status_code}")
        time.sleep(0.5)
    
    # Clean up
    if rule and 'id' in rule:
        requests.delete(f"{API_URL}/{rule['id']}")

def test_multipart_form():
    """Test multipart form data field extraction."""
    print("\n=== Testing Multipart Form Data Field Extraction ===")
    
    # Create rule for multipart
    rule = create_body_rate_limit_rule("session_id", "multipart/form-data")
    
    # Test requests with different session IDs
    session_ids = ["sess123", "sess456", "sess123"]
    
    for session_id in session_ids:
        files = {
            'session_id': (None, session_id),
            'action': (None, 'test')
        }
        
        response = requests.post(
            f"{BASE_URL}{TEST_PATH}",
            files=files,
            timeout=10
        )
        print(f"  Request with session_id={session_id}: {response.status_code}")
        time.sleep(0.5)
    
    # Clean up
    if rule and 'id' in rule:
        requests.delete(f"{API_URL}/{rule['id']}")

def main():
    print("Testing Multi-Content-Type Body Field Extraction for Rate Limiting")
    print("=" * 70)
    
    try:
        # Test each content type
        test_json_body()
        time.sleep(1)
        
        test_form_data()
        time.sleep(1)
        
        test_xml_body()
        time.sleep(1)
        
        test_multipart_form()
        
        print("\n" + "=" * 70)
        print("Tests completed!")
        print("\nNote: Each test creates a rule that allows 5 requests per 60 seconds")
        print("Rate limiting is based on the extracted field value, not IP address.")
        
    except Exception as e:
        print(f"\nError during testing: {e}")
        print("Make sure the gateway is running on http://localhost:8080")

if __name__ == "__main__":
    main()

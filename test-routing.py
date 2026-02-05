#!/usr/bin/env python3
"""Quick test to see what error the gateway returns"""

import requests
import json

url = 'http://localhost:8080/test/api/hello'
print(f"Testing: {url}")
print("-" * 60)

try:
    r = requests.get(url, timeout=5)
    print(f"Status Code: {r.status_code}")
    print(f"Content-Type: {r.headers.get('content-type')}")
    print("\nResponse Headers:")
    for k, v in r.headers.items():
        print(f"  {k}: {v}")
    print("\nResponse Body:")
    try:
        data = r.json()
        print(json.dumps(data, indent=2))
    except:
        print(r.text)
except Exception as e:
    print(f"Error: {e}")

#!/usr/bin/env python3
"""
Simple Python test server that exposes a hello world JSON API.
This server will be proxied through the rate limiter gateway for testing.
"""

from flask import Flask, jsonify, request
from datetime import datetime
import logging
import os

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Request counter for monitoring
request_counter = 0


@app.route('/api/hello', methods=['GET'])
def hello_world():
    """Simple GET endpoint returning hello world JSON"""
    global request_counter
    request_counter += 1
    
    logger.info(f"GET /api/hello - Request #{request_counter}")
    
    return jsonify({
        'message': 'Hello, World!',
        'timestamp': datetime.now().isoformat(),
        'request_count': request_counter,
        'method': 'GET'
    })


@app.route('/api/echo', methods=['POST', 'PUT', 'PATCH'])
def echo():
    """POST/PUT/PATCH endpoint that echoes back the request data"""
    global request_counter
    request_counter += 1
    
    logger.info(f"{request.method} /api/echo - Request #{request_counter}")
    
    # Get request data
    data = request.get_json(silent=True) or {}
    
    # Log headers for debugging
    headers = {k: v for k, v in request.headers.items()}
    logger.info(f"Headers: {headers}")
    
    return jsonify({
        'message': 'Echo successful',
        'timestamp': datetime.now().isoformat(),
        'request_count': request_counter,
        'method': request.method,
        'received_data': data,
        'headers_received': {
            'X-Form-Token': request.headers.get('X-Form-Token'),
            'X-Form-Load-Time': request.headers.get('X-Form-Load-Time'),
            'X-Honeypot': request.headers.get('X-Honeypot'),
            'X-Idempotency-Key': request.headers.get('X-Idempotency-Key'),
        }
    })


@app.route('/api/status', methods=['GET'])
def status():
    """Status endpoint"""
    return jsonify({
        'status': 'online',
        'timestamp': datetime.now().isoformat(),
        'total_requests': request_counter
    })


@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({'status': 'healthy'})


if __name__ == '__main__':
    print("=" * 60)
    print("Starting Test Server on http://localhost:9000")
    print("=" * 60)
    print("\nEndpoints:")
    print("  GET  /api/hello  - Hello world JSON")
    print("  POST /api/echo   - Echo back request data")
    print("  GET  /api/status - Server status")
    print("  GET  /health     - Health check")
    print("\nThis server should be accessed through the gateway at:")
    print("  http://localhost:8080/test/...")
    print("=" * 60)

    # Enable Flask debug mode only when explicitly requested via environment
    debug_mode = os.getenv('FLASK_DEBUG') == '1'
    app.run(host='0.0.0.0', port=9000, debug=debug_mode)

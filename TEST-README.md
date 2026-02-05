# Testing Scripts

This directory contains automated testing scripts for the Rate Limiter Gateway.

## Quick Start

### Windows (PowerShell)
```powershell
# Run both server and tests
.\Run-Tests.ps1

# Or specify mode
.\Run-Tests.ps1 -Mode server  # Server only
.\Run-Tests.ps1 -Mode tests   # Tests only
.\Run-Tests.ps1 -Mode both    # Both (default)
```

### Cross-platform (Python)
```bash
# Run both server and tests
python run-tests.py

# Or specify mode
python run-tests.py server  # Server only
python run-tests.py tests   # Tests only
python run-tests.py both    # Both (default)
```

### Manual Method
```bash
# Terminal 1: Start test server
python test-server.py

# Terminal 2: Run tests
python test-gateway.py
```

## Files

- **test-server.py** - Simple Flask server exposing test endpoints
- **test-gateway.py** - Comprehensive test suite for all gateway features
- **run-tests.py** - Python test runner script
- **Run-Tests.ps1** - PowerShell test runner script (Windows)
 

## What Gets Tested

✅ Gateway routing and basic functionality  
✅ Rate limiting (rapid request handling)  
✅ Anti-bot honeypot field detection  
✅ Anti-bot timing checks  
✅ One-time token validation  
✅ Token reuse prevention  
✅ Idempotency key handling  
✅ Meta refresh challenge mechanism  

## Dependencies

Install Python dependencies before running tests:

```bash
pip install flask requests
```

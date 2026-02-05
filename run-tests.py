#!/usr/bin/env python3
"""
Quick start script for running the test server and optionally the test suite.
"""

import subprocess
import sys
import time
import os
import signal

def print_banner():
    print("=" * 70)
    print("  Rate Limiter Gateway - Test Environment")
    print("=" * 70)
    print()

def check_dependencies():
    """Check if required Python packages are installed"""
    try:
        import flask
        import requests
        return True
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        print("\nPlease install dependencies:")
        print("  pip install -r test-requirements.txt")
        print("\nOr manually:")
        print("  pip install flask requests")
        return False

def start_test_server():
    """Start the test server in the background"""
    print("Starting test server on http://localhost:9000...")
    proc = subprocess.Popen(
        [sys.executable, "test-server.py"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        universal_newlines=True,
        bufsize=1
    )
    
    # Wait a bit for the server to start
    time.sleep(2)
    
    # Check if process is still running
    if proc.poll() is not None:
        print("❌ Test server failed to start!")
        return None
    
    print("Test server started (PID: {})".format(proc.pid))
    return proc

def run_tests():
    """Run the test suite"""
    print("\nRunning test suite...")
    print("=" * 70)

    test_files = [
        "test-gateway.py",
        "test-jwt-rate-limit.py",
        "test-body-content-types.py",
    ]

    exit_code = 0
    for test_file in test_files:
        if not os.path.exists(test_file):
            print(f"Skipping missing test file: {test_file}")
            continue

        print("\n" + "=" * 70)
        print(f"Running {test_file}")
        print("=" * 70)
        result = subprocess.run([sys.executable, test_file])
        if result.returncode != 0:
            exit_code = result.returncode

    return exit_code

def main():
    print_banner()
    
    # Check if dependencies are installed
    if not check_dependencies():
        sys.exit(1)
    
    # Determine mode
    mode = "both"  # default
    if len(sys.argv) > 1:
        mode = sys.argv[1].lower()
    
    if mode not in ["server", "tests", "both"]:
        print("Usage: python run-tests.py [server|tests|both]")
        print("  server - Start test server only")
        print("  tests  - Run tests only (assumes server is already running)")
        print("  both   - Start server and run tests (default)")
        sys.exit(1)
    
    server_proc = None
    
    try:
        if mode in ["server", "both"]:
            server_proc = start_test_server()
            if server_proc is None:
                sys.exit(1)
        
        if mode == "server":
            print("\nTest server is running. Press Ctrl+C to stop.")
            print("   You can now run tests with: python run-tests.py tests")
            # Keep running
            try:
                server_proc.wait()
            except KeyboardInterrupt:
                print("\n\n⏹️  Stopping test server...")
        
        elif mode in ["tests", "both"]:
            if mode == "both":
                print("\nGiving server a moment to fully initialize...")
                time.sleep(1)
            
            exit_code = run_tests()
            
            if mode == "both":
                print("\nStopping test server...")
            
            sys.exit(exit_code)
    
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
    
    finally:
        # Cleanup: stop the server if we started it
        if server_proc is not None:
            try:
                server_proc.terminate()
                server_proc.wait(timeout=5)
            except:
                server_proc.kill()

if __name__ == '__main__':
    main()

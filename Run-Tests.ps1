# Quick Test Runner for Windows
# This script helps you quickly test the Rate Limiter Gateway

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("server", "tests", "both")]
    [string]$Mode = "both"
)

Write-Host "=" -ForegroundColor Cyan -NoNewline
Write-Host ("=" * 69) -ForegroundColor Cyan
Write-Host "  Rate Limiter Gateway - Test Environment" -ForegroundColor White
Write-Host "=" -ForegroundColor Cyan -NoNewline
Write-Host ("=" * 69) -ForegroundColor Cyan
Write-Host ""

# Check if Python is installed
try {
    $pythonVersion = python --version 2>&1
    Write-Host "Python found: $pythonVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Python not found! Please install Python 3.7+" -ForegroundColor Red
    exit 1
}

# Check if dependencies are installed
Write-Host "`nChecking Python dependencies..." -ForegroundColor Yellow
$checkDeps = python -c "import flask, requests" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Missing Python dependencies!" -ForegroundColor Red
    Write-Host "`nInstalling dependencies..." -ForegroundColor Yellow
    pip install -r test-requirements.txt
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to install dependencies!" -ForegroundColor Red
        exit 1
    }
}
Write-Host "Dependencies OK" -ForegroundColor Green

function Run-TestSuite {
    $testFiles = @(
        "test-gateway.py",
        "test-jwt-rate-limit.py",
        "test-body-content-types.py"
    )

    $exitCode = 0
    foreach ($testFile in $testFiles) {
        if (-not (Test-Path $testFile)) {
            Write-Host "Skipping missing test file: $testFile" -ForegroundColor Yellow
            continue
        }

        Write-Host "";
        Write-Host ("=" * 70) -ForegroundColor Cyan
        Write-Host "Running $testFile" -ForegroundColor White
        Write-Host ("=" * 70) -ForegroundColor Cyan

        python $testFile
        if ($LASTEXITCODE -ne 0) {
            $exitCode = $LASTEXITCODE
        }
    }

    return $exitCode
}

# Run based on mode
switch ($Mode) {
    "server" {
        Write-Host "`nStarting test server only..." -ForegroundColor Cyan
        Write-Host "   Press Ctrl+C to stop" -ForegroundColor Gray
        Write-Host ""
        python test-server.py
    }
    "tests" {
        Write-Host "`nRunning test suite..." -ForegroundColor Cyan
        Write-Host "   (Assumes test server is already running)" -ForegroundColor Gray
        Write-Host ""
        $testExitCode = Run-TestSuite
        exit $testExitCode
    }
    "both" {
        Write-Host "`nStarting test server..." -ForegroundColor Cyan
        $serverJob = Start-Job -ScriptBlock {
            Set-Location $using:PWD
            python test-server.py
        }
        
        Write-Host "Waiting for server to initialize..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3
        
        # Check if server started successfully
        $jobState = Get-Job -Id $serverJob.Id | Select-Object -ExpandProperty State
        if ($jobState -ne "Running") {
            Write-Host "Test server failed to start!" -ForegroundColor Red
            Receive-Job -Job $serverJob
            Remove-Job -Job $serverJob
            exit 1
        }
        
        Write-Host "Test server started" -ForegroundColor Green
        Write-Host ""
        Write-Host "Running test suite..." -ForegroundColor Cyan
        Write-Host "=" -ForegroundColor Cyan -NoNewline
        Write-Host ("=" * 69) -ForegroundColor Cyan

        $testExitCode = Run-TestSuite
        
        Write-Host ""
        Write-Host "Stopping test server..." -ForegroundColor Yellow
        Stop-Job -Job $serverJob
        Remove-Job -Job $serverJob
        
        exit $testExitCode
    }
}

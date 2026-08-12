# Starts every service AlphaGraph's local dev environment needs: Postgres (project-dedicated
# instance, port 5434), the Python NLP sidecar (port 8000), the Spring Boot backend (port 8080),
# and the Vite frontend (port 5173). Postgres runs detached (it's a real background service,
# nothing to watch); the other three each get their own PowerShell window so you can see their
# logs and Ctrl+C any one of them independently without taking the others down.
#
# Usage: right-click > Run with PowerShell, or from a terminal: .\start-dev.ps1

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot

$pgBin   = "D:\Program Files\PostgreSQL\17\bin"
$pgData  = "D:\pgdata"
$pgPort  = 5434
$sidecarPort = 8000
$backendPort = 8080
$frontendPort = 5173

function Test-PortOpen {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host "== AlphaGraph dev environment startup ==" -ForegroundColor Cyan

# 1. Postgres (project-dedicated instance - never the shared postgresql-x64-17 Windows service)
if (Test-PortOpen $pgPort) {
    Write-Host "[postgres]    already running on port $pgPort" -ForegroundColor Yellow
} else {
    Write-Host "[postgres]    starting..." -ForegroundColor Green
    & "$pgBin\pg_ctl.exe" -D $pgData -o "-p $pgPort" -l "$pgData\startup.log" start | Out-Null
    Start-Sleep -Seconds 3
    if (Test-PortOpen $pgPort) {
        Write-Host "[postgres]    up on port $pgPort" -ForegroundColor Green
    } else {
        Write-Host "[postgres]    did not come up - check $pgData\startup.log" -ForegroundColor Red
    }
}

# 2. NLP sidecar (Python/FastAPI) - own window, since it's a foreground process
if (Test-PortOpen $sidecarPort) {
    Write-Host "[nlp-sidecar] already running on port $sidecarPort" -ForegroundColor Yellow
} else {
    Write-Host "[nlp-sidecar] launching in a new window (takes ~20-30s to load the spaCy model)..." -ForegroundColor Green
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "cd '$repoRoot\nlp-sidecar'; .\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port $sidecarPort"
    )
}

# 3. Spring Boot backend - own window
if (Test-PortOpen $backendPort) {
    Write-Host "[backend]     already running on port $backendPort" -ForegroundColor Yellow
} else {
    Write-Host "[backend]     launching in a new window..." -ForegroundColor Green
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "cd '$repoRoot'; `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=D:/tmp'; `$env:SPRING_PROFILES_ACTIVE='local'; .\gradlew.bat :bootstrap:bootRun"
    )
}

# 4. Vite frontend - own window
if (Test-PortOpen $frontendPort) {
    Write-Host "[frontend]    already running on port $frontendPort" -ForegroundColor Yellow
} else {
    Write-Host "[frontend]    launching in a new window..." -ForegroundColor Green
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "cd '$repoRoot\web'; npm run dev"
    )
}

Write-Host "`nAll services launching. Postgres runs detached in the background;" -ForegroundColor Cyan
Write-Host "nlp-sidecar / backend / frontend each opened their own window - Ctrl+C in a window stops just that service." -ForegroundColor Cyan
Write-Host "Backend takes ~5-10s, frontend ~1-2s, sidecar ~20-30s (spaCy model load) before they're ready." -ForegroundColor Cyan

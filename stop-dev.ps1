# Stops every service start-dev.ps1 starts: Postgres, the NLP sidecar, the Spring Boot backend,
# and the Vite frontend. Postgres gets a clean `pg_ctl stop` (avoids the "database system was
# interrupted" recovery on next start that a raw process kill causes); the other three are
# stopped by finding whichever process is actually listening on their port and stopping just
# that one - never a broad "kill all python/java/node" sweep, since those could easily catch
# something unrelated running on this machine.
#
# Usage: right-click > Run with PowerShell, or from a terminal: .\stop-dev.ps1

$ErrorActionPreference = "Continue"

$pgBin  = "D:\Program Files\PostgreSQL\17\bin"
$pgData = "D:\pgdata"
$pgPort = 5434
$sidecarPort  = 8000
$backendPort  = 8080
$frontendPort = 5173

function Stop-ByPort {
    param([string]$Name, [int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $conn) {
        Write-Host "[$Name] not running (nothing on port $Port)" -ForegroundColor Yellow
        return
    }
    try {
        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction Stop
        Write-Host "[$Name] stopped (was pid $($conn.OwningProcess) on port $Port)" -ForegroundColor Green
    } catch {
        Write-Host "[$Name] found on port $Port (pid $($conn.OwningProcess)) but couldn't stop it: $_" -ForegroundColor Red
    }
}

Write-Host "== Stopping AlphaGraph dev environment ==" -ForegroundColor Cyan

# 1. Frontend (Vite / node)
Stop-ByPort -Name "frontend" -Port $frontendPort

# 2. Backend (Spring Boot / java)
Stop-ByPort -Name "backend" -Port $backendPort

# 3. NLP sidecar (uvicorn / python)
Stop-ByPort -Name "nlp-sidecar" -Port $sidecarPort

# 4. Postgres - clean shutdown via pg_ctl, not a raw process kill
if (Get-NetTCPConnection -LocalPort $pgPort -State Listen -ErrorAction SilentlyContinue) {
    Write-Host "[postgres] stopping (pg_ctl -m fast)..." -ForegroundColor Green
    & "$pgBin\pg_ctl.exe" -D $pgData -m fast stop
} else {
    Write-Host "[postgres] not running on port $pgPort" -ForegroundColor Yellow
}

Write-Host "`nDone. The PowerShell windows start-dev.ps1 opened for the sidecar/backend/frontend stay open (now idle) - close them manually, or just leave them for next time." -ForegroundColor Cyan

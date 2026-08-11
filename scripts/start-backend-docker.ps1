# Start PulseFlow backend + infrastructure in Docker
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"
$DockerDesktop = "${env:ProgramFiles}\Docker\Docker\Docker Desktop.exe"

function Test-DockerReady {
    $service = Get-Service -Name "com.docker.service" -ErrorAction SilentlyContinue
    if ($null -eq $service -or $service.Status -ne "Running") {
        return $false
    }
    $job = Start-Job { docker version --format "{{.Server.Version}}" 2>$null }
    $done = Wait-Job $job -Timeout 8
    if (-not $done) {
        Stop-Job $job -Force | Out-Null
        Remove-Job $job -Force | Out-Null
        return $false
    }
    $ok = ($job | Receive-Job) -match "\d"
    Remove-Job $job -Force | Out-Null
    return $ok
}

if (-not (Test-DockerReady)) {
    Write-Host "Docker is not running." -ForegroundColor Yellow
    if (Test-Path $DockerDesktop) {
        Write-Host "Starting Docker Desktop — approve any UAC prompt and wait until the whale icon is steady." -ForegroundColor Yellow
        Start-Process $DockerDesktop | Out-Null
    } else {
        Write-Error "Docker Desktop not found. Install it from https://www.docker.com/products/docker-desktop/"
    }

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerReady) { break }
        Write-Host "Waiting for Docker daemon..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds 5
    }

    if (-not (Test-DockerReady)) {
        Write-Error @"
Docker did not become ready.

1. Open Docker Desktop from the Start menu.
2. Wait until it shows 'Engine running'.
3. Re-run: powershell -ExecutionPolicy Bypass -File scripts\start-backend-docker.ps1
"@
    }
}

Write-Host "Building and starting backend stack..." -ForegroundColor Cyan
Push-Location $Backend
try {
    docker compose up -d --build
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host ""
    Write-Host "Stack status:" -ForegroundColor Green
    docker compose ps

    Write-Host ""
    Write-Host "Backend:  http://localhost:8081" -ForegroundColor Green
    Write-Host "Keycloak: http://localhost:8080" -ForegroundColor Green
    Write-Host "RabbitMQ: http://localhost:15672 (guest/guest)" -ForegroundColor Green
    Write-Host ""
    Write-Host "Logs: docker compose logs -f backend" -ForegroundColor DarkGray
} finally {
    Pop-Location
}

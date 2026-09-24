<#
.SYNOPSIS
    Arranca la plataforma eTribunal en modo DEV (App Stack via Docker Compose).
.DESCRIPTION
    Idempotente: verifica que infra-local esté corriendo, luego levanta el App Stack
    via Docker Compose. Usa .env.dev para configuración y .env para secrets.
    Requiere: infra-local ya levantado en D:\Trabajo\Otros\infra-local
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition | Join-Path -ChildPath ".."
$repoRoot = Resolve-Path $repoRoot
Set-Location $repoRoot

$infraRoot = "D:\Trabajo\Otros\infra-local"

# Directorio de logs
$logDir = Join-Path $repoRoot "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Cargar .env (secrets) y .env.dev (config dev)
foreach ($envFile in @(".env", ".env.dev")) {
    $path = Join-Path $repoRoot $envFile
    if (Test-Path $path) {
        Get-Content $path | ForEach-Object {
            if ($_ -match '^\s*([^#=]+)=(.*)$') {
                $key = $matches[1].Trim()
                $val = $matches[2].Trim()
                if ($val -match '^"(.*)"$') { $val = $matches[1] }
                [Environment]::SetEnvironmentVariable($key, $val, "Process")
            }
        }
    }
}

Write-Host "============================================================"
Write-Host "  eTribunal Platform - Modo DEV (App Stack + infra-local)"
Write-Host "============================================================"

# Funciones auxiliares
function Test-PortFree($port) {
    $listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -eq $port }
    return $listeners.Count -eq 0
}

function Wait-Port($port, $name, $maxSeconds) {
    $elapsed = 0
    Write-Host "[WAIT] $name en :$port (max ${maxSeconds}s)..."
    while ((Test-PortFree $port) -and ($elapsed -lt $maxSeconds)) {
        Start-Sleep -Seconds 3
        $elapsed += 3
    }
    if (Test-PortFree $port) {
        Write-Host "[WARN] $name NO levanto en :$port tras ${maxSeconds}s"
        return $false
    }
    Write-Host "[OK]   $name UP en :$port"
    return $true
}

function Test-Service($url, $name, $timeoutSec = 5) {
    try {
        $resp = Invoke-RestMethod -Uri $url -TimeoutSec $timeoutSec -ErrorAction Stop
        return $true
    } catch {
        return $false
    }
}

# 1. Verificar Docker Desktop
Write-Host "`n[1/5] Verificando Docker Desktop..."
$dockerExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$dockerReady = $false
for ($i = 0; $i -lt 60; $i++) {
    & $env:ComSpec /c "docker info >NUL 2>&1"
    if ($LASTEXITCODE -eq 0) { $dockerReady = $true; Write-Host "       Docker listo."; break }
    if ($i -eq 10 -and (Test-Path $dockerExe)) {
        Write-Host "       Docker Desktop no responde, intentando lanzarlo..."
        Start-Process -FilePath $dockerExe | Out-Null
    }
    Start-Sleep -Seconds 3
}
if (-not $dockerReady) { Write-Error "Docker Desktop no esta disponible tras 180s. Aborta."; exit 1 }

# 2. Verificar infra-local (puertos clave)
Write-Host "`n[2/5] Verificando infra-local ($infraRoot)..."
$infraPorts = @(
    @{ Port=4566; Name="Floci S3" },
    @{ Port=7002; Name="Floci RDS Identity" },
    @{ Port=7003; Name="Floci RDS Core" },
    @{ Port=6379; Name="Redis" },
    @{ Port=9092; Name="Kafka" },
    @{ Port=9411; Name="Zipkin" }
)

$infraMissing = @()
foreach ($svc in $infraPorts) {
    if (Test-PortFree $svc.Port) {
        $infraMissing += $svc.Name
    }
}
if ($infraMissing.Count -gt 0) {
    Write-Host "[WARN] Los siguientes servicios de infra-local NO estan accesibles:"
    $infraMissing | ForEach-Object { Write-Host "       - $_" }
    Write-Host ""
    Write-Host "       Levantar infra-local:"
    Write-Host "         cd $infraRoot"
    Write-Host "         docker compose up -d"
    Write-Host ""
    $choice = Read-Host "¿Continuar de todas formas? (s/N)"
    if ($choice -notin @('s','S','y','Y')) { exit 1 }
} else {
    Write-Host "       infra-local OK (todos los puertos accesibles)."
}

# 3. Verificar red etribunal-net
Write-Host "`n[3/5] Verificando red etribunal-net..."
$net = docker network ls --format "{{.Name}}" | Where-Object { $_ -eq "etribunal-net" }
if (-not $net) {
    Write-Host "       Creando red etribunal-net..."
    docker network create etribunal-net | Out-Null
}
Write-Host "       Red etribunal-net OK."

# 4. Levantar App Stack via Docker Compose
Write-Host "`n[4/5] Levantando App Stack (docker compose --env-file .env.dev up -d --build)..."
$composeCmd = "docker compose --env-file .env.dev up -d --build"
Write-Host "       Ejecutando: $composeCmd"
$exitCode = & $env:ComSpec /c $composeCmd
if ($exitCode -ne 0) {
    Write-Error "docker compose fallo (exit code $exitCode). Revisa logs."
    exit 1
}
Write-Host "       App Stack levantado."

# 5. Health checks
Write-Host "`n[5/5] Esperando health checks de los servicios..."
$appServices = @(
    @{ Name="Gateway";      Port=8080; Health="/actuator/health" },
    @{ Name="Identity";     Port=8081; Health="/actuator/health" },
    @{ Name="Core";         Port=8082; Health="/actuator/health" },
    @{ Name="AI Engine";    Port=8083; Health="/actuator/health" },
    @{ Name="UI";           Port=3000; Health="/" }
)

foreach ($svc in $appServices) {
    $url = "http://localhost:$($svc.Port)$($svc.Health)"
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-Service $url $svc.Name 3) { $ok = $true; break }
        Start-Sleep -Seconds 3
    }
    if ($ok) { Write-Host "[OK]   $($svc.Name) UP en :$($svc.Port)" }
    else { Write-Host "[WARN] $($svc.Name) NO respondio en :$($svc.Port) tras 180s" }
}

Write-Host "`n============================================================"
Write-Host "  PLATAFORMA LISTA (Modo DEV)"
Write-Host "============================================================"
Write-Host ""
Write-Host "  Gateway API     : http://localhost:8080/api"
Write-Host "  Identity API    : http://localhost:8081/api"
Write-Host "  Core API        : http://localhost:8082/api"
Write-Host "  AI Engine WS    : ws://localhost:8083/ws/automation"
Write-Host "  UI              : http://localhost:3000"
Write-Host "  Swagger Gateway : http://localhost:8080/swagger-ui"
Write-Host ""
Write-Host "  Infra-local (externo):"
Write-Host "  Grafana         : http://localhost:3001 (admin/admin)"
Write-Host "  Prometheus      : http://localhost:9090"
Write-Host "  Tempo           : http://localhost:3200"
Write-Host "  Alloy UI        : http://localhost:12345"
Write-Host "  SonarQube       : http://localhost:9000 (admin/admin)"
Write-Host "  Floci S3        : http://localhost:4566"
Write-Host "  Floci RDS       : :7002 (identity), :7003 (core)"
Write-Host ""
Write-Host "  Logs app:  docker compose logs -f -t --tail=100"
Write-Host "  Logs infra: cd $infraRoot && docker compose logs -f -t --tail=100"
Write-Host ""
Write-Host "  Para detener App Stack:  scripts\stop-platform.bat"
Write-Host "  Para detener TODO:       scripts\stop-platform.bat --with-infra"
Write-Host "============================================================"
<#
.SYNOPSIS
    Arranca la plataforma eTribunal completa (infra + 4 servicios + UI) por logs.
.DESCRIPTION
    Idempotente: si un puerto ya esta escuchando, salta ese componente.
    Usa fat-jars pre-compilados (java -jar) para evitar el lock del proyecto Gradle
    y el cache de variables del daemon. Espera a que Docker Desktop este listo.
    Usa rutas absolutas para los logs.
#>

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition | Join-Path -ChildPath ".."
$repoRoot = Resolve-Path $repoRoot
Set-Location $repoRoot

# Directorio de logs (ruta absoluta, obligatorio para la redireccion)
$logDir = Join-Path $repoRoot "logs"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

# Cargar .env del repo raiz y setear variables de entorno para herencia
if (Test-Path (Join-Path $repoRoot ".env")) {
    Get-Content (Join-Path $repoRoot ".env") | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            if ($val -match '^"(.*)"$') { $val = $matches[1] }
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

Write-Host "============================================================"
Write-Host "  eTribunal Platform - Arranque completo"
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

# 1. Esperar Docker Desktop (puede tardar al iniciar Windows)
Write-Host "`n[1/6] Esperando Docker Desktop..."
$dockerExe = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$dockerReady = $false
for ($i = 0; $i -lt 60; $i++) {
    # cmd /c evita que los warnings a stderr de docker.exe rompan $ErrorActionPreference=Stop
    & $env:ComSpec /c "docker info >NUL 2>&1"
    if ($LASTEXITCODE -eq 0) { $dockerReady = $true; Write-Host "       Docker listo."; break }
    if ($i -eq 10 -and (Test-Path $dockerExe)) {
        Write-Host "       Docker Desktop no responde, intentando lanzarlo..."
        Start-Process -FilePath $dockerExe | Out-Null
    }
    Start-Sleep -Seconds 3
}
if (-not $dockerReady) { Write-Error "Docker Desktop no esta disponible tras 180s. Aborta."; exit 1 }

# 2. Infra
Write-Host "`n[2/6] Infraestructura (Redis + Floci + Zipkin + Kafka + S3)..."
& (Join-Path $repoRoot "scripts\infra-up.bat")
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo levantar la infraestructura"; exit 1 }

# 3. Esperar Floci
Write-Host "[3/6] Esperando Floci en :4566..."
for ($i = 0; $i -lt 60; $i++) {
    if ($null -ne (Invoke-RestMethod -Uri "http://localhost:4566/_localstack/health" -TimeoutSec 2 -ErrorAction SilentlyContinue)) { break }
    Start-Sleep -Seconds 2
}
Write-Host "       Floci listo."

# 4. Servicios backend
Write-Host "`n[4/6] Servicios backend (java -jar)..."
$services = @(
    @{ name="Identity";  jar="services\identity-service\build\libs";       port=8081; log="identity" },
    @{ name="Core";      jar="services\core-domain-service\build\libs";    port=8082; log="core" },
    @{ name="Gateway";   jar="services\gateway-service\build\libs";        port=8080; log="gateway" },
    @{ name="AI Engine"; jar="services\ai-engine-service\build\libs";      port=8083; log="ai-engine" }
)

foreach ($svc in $services) {
    Write-Host "`n$($svc.name) (:$($svc.port))..."
    if (-not (Test-PortFree $svc.port)) {
        Write-Host "       Ya esta corriendo (puerto $($svc.port) ocupado)."
        continue
    }
    $jarDir = Join-Path $repoRoot $svc.jar
    # Elegir el fat-jar (excluye *-plain.jar)
    $jar = Get-ChildItem -Path $jarDir -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-plain\.jar$' } | Select-Object -First 1
    if (-not $jar) {
        Write-Host "[ERROR] No hay fat-jar en $jarDir. Ejecuta primero: gradlew <servicio>:bootJar"
        continue
    }
    $appArgs = @("--spring.profiles.active=local")
    if ($svc.log -eq "ai-engine") {
        $aiKey = [Environment]::GetEnvironmentVariable("AI_API_KEY", "Process")
        $aiModel = [Environment]::GetEnvironmentVariable("AI_MODEL", "Process")
        if (-not [string]::IsNullOrEmpty($aiKey)) {
            $appArgs += "--spring.ai.google.genai.api-key=$aiKey"
        }
        if (-not [string]::IsNullOrEmpty($aiModel)) {
            $appArgs += "--spring.ai.google.genai.chat.options.model=$aiModel"
        }
    }
    $proc = Start-Process -FilePath "java" `
        -ArgumentList (@("-jar", "`"$($jar.FullName)`"") + $appArgs) `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput (Join-Path $logDir "$($svc.log).log") `
        -RedirectStandardError (Join-Path $logDir "$($svc.log).err.log") `
        -WindowStyle Minimized `
        -PassThru
    Write-Host "       PID $($proc.Id) -> logs\$($svc.log).log"
}

# 5. Frontend UI
Write-Host "`n[5/6] Frontend UI (:3000)..."
$uiDir = Join-Path (Split-Path $repoRoot) "etribunal-ui"
if (Test-PortFree 3000 -and (Test-Path $uiDir)) {
    Write-Host "       Levantando pnpm dev en $uiDir ..."
    $proc = Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "pnpm dev" `
        -WorkingDirectory $uiDir `
        -RedirectStandardOutput (Join-Path $logDir "ui.log") `
        -RedirectStandardError (Join-Path $logDir "ui.err.log") `
        -WindowStyle Minimized `
        -PassThru
    Write-Host "       PID $($proc.Id) -> logs\ui.log"
} elseif (Test-PortFree 3000) {
    Write-Host "[ERROR] No existe $uiDir"
} else {
    Write-Host "       Ya esta corriendo (puerto 3000 ocupado)."
}

# 6. Health checks (con timeout por servicio)
Write-Host "`n[6/6] Esperando health checks..."
foreach ($svc in $services) {
    if (-not (Test-PortFree $svc.port)) { continue }
    Wait-Port $svc.port $svc.name 300 | Out-Null
}
if (Test-PortFree 3000) { Wait-Port 3000 "UI" 120 | Out-Null }

Write-Host "`n============================================================"
Write-Host "  PLATAFORMA LISTA"
Write-Host "============================================================"
Write-Host ""
Write-Host "  Gateway API     : http://localhost:8080/api"
Write-Host "  AI Engine WS    : ws://localhost:8083/ws/automation"
Write-Host "  Panel Admin     : http://localhost:3000/admin/motor-ia"
Write-Host "  UI              : http://localhost:3000"
Write-Host ""
Write-Host "  Logs en: .\logs\*.log  (y .err.log para errores)"
Write-Host ""
Write-Host "  Para detener:  scripts\stop-platform.bat"
Write-Host "============================================================"
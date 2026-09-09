@echo off
REM eTribunal - Levanta TODO en Docker (infra + 4 servicios + UI) en local.
REM Uso: scripts\docker-up.bat
REM
REM Pre-requisito: Docker Desktop corriendo.
REM Comportamiento:
REM   1. Compila los fat-jars de los 4 servicios si no existen (gradlew bootJar).
REM   2. docker compose build (backend + UI).
REM   3. docker compose --profile app --profile floci-local up -d  (con kafka opcional via %2)
setlocal
cd /d "%~dp0.."

echo ============================================================
echo  eTribunal Platform - Docker UP (todo en contenedores)
echo ============================================================

echo.
echo [1/4] Verificando Docker...
docker info >NUL 2>&1
if errorlevel 1 (
    echo [ERROR] Docker no esta corriendo. Abre Docker Desktop y reintenta.
    exit /b 1
)
echo       Docker OK.

echo.
echo [2/4] Bootstrap proyectos...
call gradlew.bat :services:identity-service:bootJar :services:core-domain-service:bootJar :services:gateway-service:bootJar :services:ai-engine-service:bootJar --console=plain
if errorlevel 1 (
    echo [ERROR] Fallo al compilar jars. Revisa logs de Gradle.
    exit /b 1
)

echo.
echo [3/4] Construyendo imagenes (backend + UI)...
call docker compose --profile app --profile floci-local build
if errorlevel 1 (
    echo [ERROR] Fallo en docker compose build.
    exit /b 1
)

echo.
echo [4/4] Levantando contenedores...
if /i "%~1"=="all" (
    call docker compose --profile app --profile floci-local --profile zipkin up -d
) else (
    call docker compose --profile app --profile floci-local up -d
)
if errorlevel 1 (
    echo [ERROR] Fallo al levantar los contenedores.
    exit /b 1
)

echo.
echo ============================================================
echo  PLATAFORMA LISTA (Docker)
echo ============================================================
echo.
echo  Gateway API     : http://localhost:8080/api
echo  AI Engine WS    : ws://localhost:8083/ws/automation
echo  Panel Admin     : http://localhost:3000/admin/motor-ia
echo  UI              : http://localhost:3000
echo  Swagger         : http://localhost:8080/swagger-ui
echo.
echo  Opcional: scripts\docker-up.bat all   ^(tambien Zipkin /tracing^)
echo  Para ver logs:  docker compose logs -f -t --tail=100
echo  Para detener:   scripts\docker-down.bat
echo ============================================================
exit /b 0
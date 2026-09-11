@echo off
REM eTribunal - Levanta TODO en Docker (infra + 4 servicios + UI) en local.
REM Uso: scripts\docker-up.bat [all] [obs]
REM
REM   all  : tambien Zipkin (:9411, tracing)
REM   obs  : tambien Observabilidad LGTM (Prometheus + Grafana :3001 + Tempo + Loki + Alloy).
REM          Aplica el overlay docker-compose.observability.yml: las apps emiten logs JSON
REM          estructurados (LOGGING_STRUCTURED_FORMAT=ecs) y mandan trazas a Tempo.
REM
REM Ambos flags pueden combinarse: scripts\docker-up.bat all obs
REM
REM Pre-requisito: Docker Desktop corriendo.
REM Comportamiento:
REM   1. Compila los fat-jars de los 4 servicios si no existen (gradlew bootJar).
REM   2. docker compose build (backend + UI).
REM   3. docker compose --profile app --profile floci-local up -d  (+ zipkin/observability segun flags)
setlocal
cd /d "%~dp0.."

set OBS=0
set ZIPKIN=0
:parse
if /i "%~1"=="obs" set OBS=1
if /i "%~1"=="all" set ZIPKIN=1
shift
if not "%~1"=="" goto :parse

set "COMPOSE_FILES=-f docker-compose.yml"
if "%OBS%"=="1" set "COMPOSE_FILES=%COMPOSE_FILES% -f docker-compose.observability.yml"
set "PROFILES=--profile app --profile floci-local"
if "%OBS%"=="1" set "PROFILES=%PROFILES% --profile observability"
if "%ZIPKIN%"=="1" set "PROFILES=%PROFILES% --profile zipkin"

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
call docker compose %COMPOSE_FILES% %PROFILES% up -d
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
if "%OBS%"=="1" (
echo.
echo  [Observabilidad LGTM]
echo  Grafana          : http://localhost:3001   ^(admin/admin^)
echo  Prometheus       : http://localhost:9090
echo  Tempo            : http://localhost:3200/search
echo  Loki             : http://localhost:3100
echo  Alloy ^(UI^)       : http://localhost:12345
)
echo.
echo  Opcional: scripts\docker-up.bat all  ^(Zipkin /tracing^) ^| obs ^(Observabilidad^)
if "%OBS%"=="1" (
echo  Para ver logs:  docker compose -f docker-compose.yml -f docker-compose.observability.yml logs -f -t --tail=100
) else (
echo  Para ver logs:  docker compose -f docker-compose.yml logs -f -t --tail=100
)
echo  Para detener:   scripts\docker-down.bat
echo ============================================================
exit /b 0
@echo off
REM eTribunal - Levanta TODO en Docker (infra + 4 servicios + UI) + Observabilidad LGTM.
REM Uso: scripts\observability-up.bat
REM
REM Pre-requisito: Docker Desktop corriendo.
REM Incluye el overlay docker-compose.observability.yml que activa en las apps:
REM   - logs JSON estructurados (LOGGING_STRUCTURED_FORMAT=ecs)
REM   - trazas a Tempo en vez de Zipkin (MANAGEMENT_ZIPKIN_TRACING_ENDPOINT)
REM Stack: Prometheus + Grafana (:3001) + Tempo (:3200/:9411) + Loki (:3100) + Alloy.
setlocal
cd /d "%~dp0.."

echo ============================================================
echo  eTribunal Platform - Docker UP + Observabilidad (LGTM)
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
echo [4/4] Levantando contenedores (apps + observabilidad)...
call docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile app --profile floci-local --profile observability up -d
if errorlevel 1 (
    echo [ERROR] Fallo al levantar los contenedores.
    exit /b 1
)

echo.
echo ============================================================
echo  PLATAFORMA + OBSERVABILIDAD LISTAS
echo ============================================================
echo.
echo  Gateway API       : http://localhost:8080/api
echo  AI Engine WS      : ws://localhost:8083/ws/automation
echo  Panel Admin       : http://localhost:3000/admin/motor-ia
echo  UI                : http://localhost:3000
echo  Swagger           : http://localhost:8080/swagger-ui
echo.
echo  Grafana           : http://localhost:3001   ^(admin/admin; datasources auto-configurados^)
echo  Prometheus        : http://localhost:9090
echo  Tempo             : http://localhost:3200/search
echo  Loki              : http://localhost:3100
echo  Alloy (UI)        : http://localhost:12345
echo.
echo  Para ver logs:    docker compose -f docker-compose.yml -f docker-compose.observability.yml logs -f -t --tail=100
echo  Para detener:     scripts\docker-down.bat
echo ============================================================
exit /b 0
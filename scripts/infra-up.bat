@echo off
REM eTribunal dev: levanta la infra local (Redis + Floci + Zipkin + Kafka + bucket S3)
REM Uso: scripts\infra-up.bat  [temporal]
REM
REM - Redis    : se levanta siempre (docker compose).
REM - Floci    : si ya hay una instancia compartida/externa en :4566 se reusa;
REM              si no, se levanta el profile floci-local (con floci-init que crea el bucket S3).
REM - Zipkin   : profile opt-in en el compose, se levanta siempre (tracing :9411).
REM - Kafka    : profile opt-in en el compose, se levanta siempre (eventos :9092).
REM - Bucket S3 etribunal-media se asegura siempre (idempotente).
setlocal
cd /d "%~dp0.."

echo [1/5] Redis...
call docker compose up -d redis
if errorlevel 1 goto :err

echo [2/5] Floci...
curl.exe -s --max-time 3 http://localhost:4566/_localstack/health >NUL
if errorlevel 1 (
    echo   No hay Floci en :4566. Levantando profile floci-local, incluye floci-init que crea el bucket S3...
    call docker compose --profile floci-local up -d
    if errorlevel 1 goto :err
) else (
    echo   Floci ya esta arriba como instancia compartida o externa. Reusando.
)

echo [3/5] Zipkin (:9411)...
call docker compose --profile zipkin up -d
if errorlevel 1 goto :err

echo [4/5] Kafka (:9092)...
call docker compose --profile kafka up -d
if errorlevel 1 goto :err

echo [5/5] Bucket S3 etribunal-media...
aws --endpoint-url http://localhost:4566 s3 mb s3://etribunal-media
if errorlevel 1 goto :err

if /i "%~1"=="temporal" call docker compose --profile temporal up -d

echo.
echo Infra lista:
echo   Redis  :6379      contenedor etribunal-redis
echo   Floci  :4566      RDS emulated :7001-7099, S3 etribunal-media
echo   Zipkin :9411      contenedor etribunal-zipkin (tracing)
echo   Kafka  :9092      contenedor etribunal-kafka (eventos)
echo   Temporal: solo con el argumento temporal
echo.
echo Siguiente paso: levantar los 4 servicios desde VS Code (Tasks: Run Task ^> "Start eTribunal projects")
exit /b 0

:err
echo [ERROR] No se pudo levantar la infra. Revisa que Docker este corriendo.
exit /b 1
@echo off
REM eTribunal - Levanta el servidor SonarQube (Community Build) en :9000.
REM Uso: scripts\sonar-up.bat
REM
REM   Web : http://localhost:9000  (admin/admin -> cambiar password -> generar token)
REM Pre-requisito: Docker Desktop corriendo.
setlocal
cd /d "%~dp0.."

echo ============================================================
echo  eTribunal Platform - SonarQube UP (calidad de codigo)
echo ============================================================

echo.
echo [1/2] Verificando Docker...
docker info >NUL 2>&1
if errorlevel 1 (
    echo [ERROR] Docker no esta corriendo. Abre Docker Desktop y reintenta.
    exit /b 1
)
echo       Docker OK.

echo.
echo [2/2] Levantando SonarQube (primera vez descarga la imagen ~1GB)...
call docker compose -f docker-compose.yml -f docker-compose.sonarqube.yml --profile sonarqube up -d
if errorlevel 1 (
    echo [ERROR] Fallo al levantar SonarQube.
    exit /b 1
)

echo.
echo Esperando a que SonarQube arranque (puede tardar 1-2 min)...
REM /api/system/status no requiere auth (el endpoint /health devuelve 403 sin token).
set /a tries=0
:wait
curl.exe -sf http://localhost:9000/api/system/status >NUL 2>&1
if not errorlevel 1 goto :ready
set /a tries+=1
if %tries% geq 60 (
    echo [WARN] Sin respuesta aun. Dale un momento mas y revisa los logs:
    echo       docker logs etribunal-sonarqube
    goto :end
)
REM `timeout` no acepta stdin redirigido (aborta en algunos shells); `ping -n` da ~5s.
ping -n 6 127.0.0.1 >NUL
goto :wait

:ready
echo.
echo ============================================================
echo  SONARQUBE LISTO  -  http://localhost:9000
echo ============================================================
echo  Primer uso:
echo   1. Login con admin / admin  ^(el primer login pide cambiar la contrasena^).
echo   2. My Account > Security > Tokens: genera un token (p.ej. "etribunal-local").
echo   3. Guarda el token y configuralo en la sesion:
echo        set SONAR_TOKEN=tu_token_aqui
echo   4. Lanza el analisis:
echo        scripts\sonar-scan.bat
echo.
echo  Para detener: scripts\sonar-down.bat  ^(conserva los datos^)
echo ============================================================

:end
exit /b 0
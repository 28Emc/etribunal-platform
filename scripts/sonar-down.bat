@echo off
REM eTribunal - Detiene el servidor SonarQube (conserva los volumenes de datos).
REM Uso: scripts\sonar-down.bat
setlocal
cd /d "%~dp0.."

echo Deteniendo SonarQube...
call docker compose -f docker-compose.yml -f docker-compose.sonarqube.yml --profile sonarqube down
if errorlevel 1 (
    echo [ERROR] Fallo al detener SonarQube.
    exit /b 1
)
echo SonarQube detenido. Datos conservados en los volumenes sonarqube-*.
echo Para levantar de nuevo: scripts\sonar-up.bat
exit /b 0
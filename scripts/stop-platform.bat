@echo off
REM eTribunal - Detiene todos los servicios de la plataforma por puerto
REM Uso: scripts\stop-platform.bat
setlocal enabledelayedexpansion

cd /d "%~dp0.."

echo ============================================================
echo  eTribunal Platform - Deteniendo servicios
echo ============================================================

REM Funcion para matar proceso por puerto
REM Uso: call :kill_port <puerto> <nombre>
:kill_port
set PORT=%1
set NAME=%2
echo.
echo [STOP] %NAME% (puerto %PORT%)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT%') do (
    echo       Matando PID %%a...
    taskkill /F /PID %%a >NUL 2>&1
)
echo       OK.
goto :eof

call :kill_port 8083 "AI Engine"
call :kill_port 8080 "Gateway"
call :kill_port 8082 "Core Domain"
call :kill_port 8081 "Identity"
call :kill_port 3000 "Frontend UI"

echo.
echo ============================================================
echo  SERVICIOS DETENIDOS
echo ============================================================
echo.
echo  La infraestructura (Redis/Floci/Kafka/Zipkin) sigue corriendo en Docker.
echo  Para pararla: docker compose down
echo ============================================================
@echo off
REM eTribunal - Detiene TODOS los contenedores del compose (infra + servicios + UI).
REM Uso: scripts\docker-down.bat
setlocal
cd /d "%~dp0.."

echo [1/2] Deteniendo contenedores...
call docker compose --profile app --profile floci-local down --remove-orphans
if errorlevel 1 (
    echo [ERROR] Fallo al detener contenedores.
    exit /b 1
)

echo.
echo [2/2] Estado final:
call docker ps --filter name=etribunal- --format "table {{.Names}}\t{{.Status}}"
echo.
echo Plataforma detenida. Para limpiar tambien imagenes/volumenes (opcional):
echo   docker compose down --rmi all -v
exit /b 0
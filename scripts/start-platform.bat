@echo off
REM eTribunal - Arranque completo de la plataforma (infra + 4 servicios + UI)
REM Uso: scripts\start-platform.bat
REM Idempotente: si un puerto ya esta escuchando, salta ese componente.
REM Delega la logica completa a start-platform.ps1 (PowerShell) para logs fiables.

cd /d "%~dp0.."

echo ============================================================
echo  eTribunal Platform - Arranque completo
echo ============================================================

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-platform.ps1"
if errorlevel 1 (
    echo [ERROR] Fallo en el arranque.
    exit /b 1
)

echo.
echo ============================================================
echo  PLATAFORMA LISTA
echo ============================================================
echo.
echo  Gateway API     : http://localhost:8080/api
echo  AI Engine WS    : ws://localhost:8083/ws/automation
echo  Panel Admin     : http://localhost:3000/admin/motor-ia
echo  UI              : http://localhost:3000
echo.
echo  Logs en: .\logs\*.log  (y .err.log para errores)
echo.
echo  Para detener:  scripts\stop-platform.bat
echo ============================================================
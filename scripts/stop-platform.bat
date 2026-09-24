@echo off
REM eTribunal - Detiene App Stack (y opcionalmente infra-local)
REM Uso: scripts\stop-platform.bat [--with-infra]
REM   --with-infra  : tambien detiene infra-local en D:\Trabajo\Otros\infra-local
setlocal enabledelayedexpansion

cd /d "%~dp0.."

set WITH_INFRA=0
if "%~1"=="--with-infra" set WITH_INFRA=1

echo ============================================================
echo  eTribunal Platform - Deteniendo servicios
echo ============================================================

echo.
echo [1/2] Deteniendo App Stack (docker compose)...
docker compose down
if errorlevel 1 (
    echo [WARN] docker compose down fallo (quizas ya estaba abajo).
) else (
    echo [OK] App Stack detenido.
)

if %WITH_INFRA%==1 (
    echo.
    echo [2/2] Deteniendo infra-local (D:\Trabajo\Otros\infra-local)...
    cd /d "D:\Trabajo\Otros\infra-local"
    docker compose down
    if errorlevel 1 (
        echo [WARN] infra-local down fallo.
    ) else (
        echo [OK] infra-local detenido.
    )
    cd /d "%~dp0.."
) else (
    echo.
    echo [INFO] infra-local NO detenido (usa --with-infra para detenerlo).
    echo        Infra-local sigue corriendo en D:\Trabajo\Otros\infra-local
)

echo.
echo ============================================================
echo  DETENIDO
echo ============================================================
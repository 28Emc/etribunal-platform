@echo off
REM eTribunal - Core Domain Service (8082). Equivale al task VS Code "Start eTribunal core-domain-service".
cd /d "%~dp0.."
REM Cargar variables de entorno desde .env (raíz del repo) si existe.
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)
call gradlew.bat :services:core-domain-service:bootRun --args="--spring.profiles.active=local"
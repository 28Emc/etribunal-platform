@echo off
REM eTribunal - AI Engine Service (8083). Equivale al task VS Code "Start eTribunal ai-engine-service".
cd /d "%~dp0.."
REM Cargar variables de entorno desde .env (raíz del repo) si existe.
if exist ".env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do set "%%a=%%b"
)
call gradlew.bat :services:ai-engine-service:bootRun --args="--spring.profiles.active=local"
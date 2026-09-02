@echo off
REM eTribunal - Gateway Service (8080). Equivale al task VS Code "Start eTribunal gateway-service".
cd /d "%~dp0.."
call gradlew.bat :services:gateway-service:bootRun --args="--spring.profiles.active=local"
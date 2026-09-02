@echo off
REM eTribunal - Identity Service (8081). Equivale al task VS Code "Start eTribunal identity-service".
cd /d "%~dp0.."
call gradlew.bat :services:identity-service:bootRun --args="--spring.profiles.active=local"
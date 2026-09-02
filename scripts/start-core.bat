@echo off
REM eTribunal - Core Domain Service (8082). Equivale al task VS Code "Start eTribunal core-domain-service".
cd /d "%~dp0.."
call gradlew.bat :services:core-domain-service:bootRun --args="--spring.profiles.active=local"
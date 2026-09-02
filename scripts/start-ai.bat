@echo off
REM eTribunal - AI Engine Service (8083). Equivale al task VS Code "Start eTribunal ai-engine-service".
cd /d "%~dp0.."
call gradlew.bat :services:ai-engine-service:bootRun --args="--spring.profiles.active=local"
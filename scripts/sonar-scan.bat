@echo off
REM eTribunal - Analisis SonarQube de backend + frontend.
REM Uso: scripts\sonar-scan.bat
REM
REM Requiere:
REM   1. Servidor arriba: scripts\sonar-up.bat
REM   2. Token configurado: set SONAR_TOKEN=<token>   (generar en :9000, My Account > Security > Tokens)
REM   3. JDK 17+ en PATH (o JAVA_HOME) para el scanner de Gradle y el de npm (frontend).
REM
REM Que analiza:
REM   Backend  : gradlew build sonarAll  -> 7 modulos (4 services + 3 libs), cobertura JaCoCo.
REM   Frontend : pnpm run sonar       -> etribunal-ui, cobertura Vitest (LCOV).
setlocal
cd /d "%~dp0.."

if "%SONAR_TOKEN%"=="" (
    echo [ERROR] SONAR_TOKEN no esta configurado.
    echo   Genera un token en http://localhost:9000  ^(My Account - Security - Tokens^).
    echo   Luego:  set SONAR_TOKEN=tu_token
    exit /b 1
)

REM Ambos scanners (Gradle -7.x y npm) apuntan a sonarcloud.io por defecto:
REM hay que redirigirlos al server local salvo que SONAR_HOST_URL ya este seteado.
if "%SONAR_HOST_URL%"=="" set SONAR_HOST_URL=http://localhost:9000

echo ============================================================
echo  eTribunal Platform - SonarQube SCAN (backend + frontend)
echo ============================================================

echo.
echo [1/3] Backend: build + tests + cobertura + sonar (7 modulos)...
call gradlew.bat build sonarAll --console=plain
if errorlevel 1 (
    echo [ERROR] Fallo el analisis del backend.
    exit /b 1
)

echo.
echo [2/3] Frontend: coverage Vitest + sonar  ^(etribunal-ui^)...
pushd ..\etribunal-ui
call pnpm run sonar
set FERR=%ERRORLEVEL%
popd
if not "%FERR%"=="0" (
    echo [ERROR] Fallo el analisis del frontend.
    exit /b 1
)

echo.
echo [3/3] Hecho.
echo============================================================
echo  Revisa los proyectos en  http://localhost:9000
echo  Backend: gateway-service, identity-service, core-domain-service,
echo           ai-engine-service, common-domain, common-kafka, common-security
echo  Frontend: etribunal-ui
echo============================================================
exit /b 0
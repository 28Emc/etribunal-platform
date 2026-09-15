# SonarQube — Calidad de código

SonarQube (**Community Build**, self-managed) analiza el backend etribunal-platform y el
frontend etribunal-ui: bugs, code smells, security hotspots, duplicados y **cobertura de tests**.

## Arquitectura

| Componente | Detalle |
|---|---|
| Servidor | `sonarqube:26.9.0.129388-community` vía Docker (overlay `docker-compose.sonarqube.yml`), `http://localhost:9000` |
| Analizador backend | Plugin Gradle `org.sonarqube` 7.4.0.8496 + **JaCoCo** 0.8.13 (reportes XML por módulo) |
| Analizador frontend | `sonar-scanner-npm` (`sonarqube-scanner` 5.0.0) + **Vitest** `@vitest/coverage-v8` (reporte LCOV) |
| Cobertura | Backend: JaCoCo (`jacocoTestReport.xml`). Frontend: `coverage/lcov.info` |

## Proyectos que se generan

| Fuente | projectKey |
|---|---|
| `identity-service` | `identity-service` |
| `core-domain-service` | `core-domain-service` |
| `gateway-service` | `gateway-service` |
| `ai-engine-service` | `ai-engine-service` |
| `libs:common-domain` | `common-domain` |
| `libs:common-kafka` | `common-kafka` |
| `libs:common-security` | `common-security` |
| `etribunal-ui` | `etribunal-ui` |

Fuera del análisis a propósito: `libs:common-test` (fixtures), `tests:e2e` (integración gated) y
los proyectos "grouper" de Gradle (`:libs`, `:services`, `:tests`).

## Quickstart

```bat
REM 1. Levantar el servidor (primera vez descarga ~1 GB)
scripts\sonar-up.bat

REM 2. Primer login: admin / admin  ->  cambiar contraseña
REM    My Account > Security > Tokens  ->  crear token (p.ej. "etribunal-local")

REM 3. Configurar el token en la sesión
set SONAR_TOKEN=tu_token_aqui

REM 4. Analizar backend + frontend
scripts\sonar-scan.bat
```

Resultado: en `http://localhost:9000` aparecen los 8 proyectos con cobertura.

### Por separado

```bat
REM Solo backend (7 módulos, requiere SONAR_TOKEN)
set SONAR_TOKEN=...
gradlew build sonarAll --console=plain
```

```powershell
# Solo frontend (corre la cobertura y luego el análisis)
$env:SONAR_TOKEN = "tu_token_aqui"
pnpm run sonar
```

### Detener el servidor

```bat
scripts\sonar-down.bat   :: conserva los volumenes de datos (sonarqube-*)
```

## Cómo configurarlo por dentro

- **Backend** (`build.gradle.kts`): el plugin se aplica solo a los módulos con `src/main/java`.
  Cada módulo define `sonar.projectKey`, rutas de fuentes/tests y `sonar.java.binaries/libraries`.
  `gradlew sonarAll` encadena los 7 `sonar` por módulo. Los XML de JaCoCo nacen en
  `<módulo>/build/reports/jacoco/test/jacocoTestReport.xml` (task `jacocoTestReport`, wired a `test`).
- **Frontend** (`sonar-project.properties` + `package.json`): el script `sonar` corre Vitest con
  cobertura y luego `sonar-scanner-npm`. El scanner lee `SONAR_TOKEN`/`SONAR_HOST_URL` de entorno.

## Requisitos y límites

- **Memoria Docker**: la imagen de SonarQube pide ~2-4 GB de RAM (Docker Desktop).
- **Java**: el scanner (Gradle y npm) necesita un JDK 17+ en `PATH` o `JAVA_HOME`.
- **`SONAR_HOST_URL`**: los scanners (Gradle 7.x y npm) apuntan a `sonarcloud.io` por defecto.
  `build.gradle.kts` fuerza `sonar.host.url=http://localhost:9000` salvo que exista
  `SONAR_HOST_URL` en el entorno; `sonar-scan.bat` también setea `SONAR_HOST_URL` si falta.
- **Community Build**: no hay análisis de ramas (branches) ni decoración de PRs; el análisis corre
  sobre la rama por defecto. Suficiente para baseline local.
- El token **nunca** se commitea: va por variable de entorno de sesión.

## Notas de implementación (gotchas ya resueltos)

| Punto | Detalle |
|---|---|
| `sonar.java.libraries` | Debe pasarse como `FileCollection` (no `.asPath`): un String con `File.pathSeparator` no serializa a URI en Windows (`Illegal char <:>`). |
| `sonar.tests` y `sonar.coverage.jacoco.xmlReportPaths` | Solo se setean si existe `src/test/java` (módulos sin tests como `common-domain`/`common-kafka` fallan con "Invalid value of sonar.tests"). |
| `sonar.host.url` | Plugin 7.x apunta a SonarCloud → 403 al aprovisionar JRE; se fuerza el server local (ver arriba). |
| API local | El health endpoint (`/api/system/health`) exige auth; el readiness se sondea con `/api/system/status`. Tocar la API con token vía header `Authorization: Bearer <token>` (el Basic auth `admin:token` da 401). |

## Troubleshooting

| Síntoma | Solución |
|---|---|
| `curl` no responde en `:9000` tras minutos | `docker logs etribunal-sonarqube`; suele ser arranque/ES. Darle más RAM a Docker. |
| `failed to connect to localhost:9000` | Servidor no levantado → `scripts\sonar-up.bat`. |
| `Authentication failed` / `401` | `SONAR_TOKEN` mal generado o expirado. |
| `Analysis of project failed... no sources` | Módulo sin `src/main/java` (no debería ocurrir: el build lo excluye). |
| `java: command not found` (scanner npm) | Instalar JDK 17+ y agregarlo al `PATH`. |
| Coverage baja en dashboard | Los umbrales de Vitest son un gate local; Sonar muestra lo que hay. Subir cobertura de a poco. |
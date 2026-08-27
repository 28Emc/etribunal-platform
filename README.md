# eTribunal Platform

Backend microservicios de **eTribunal** — Java 21 + Spring Boot 3.5 + Gradle monorepo.

## Arquitectura

```
                    ┌─────────────┐
                    │  Gateway    │ :8080 (Spring Cloud Gateway)
                    │  JWT filter │
                    └──────┬──────┘
                           │
               ┌────────────┼────────────┐
               │            │            │
      ┌────────▼───┐ ┌──────▼──────┐ ┌──▼──────────┐
      │ Identity   │ │ Core Domain │ │  AI Engine   │
      │ :8081      │ │ :8082       │ │  :8083       │
      │ Auth/Users │ │ Cases/Votes │ │  Automation  │
      └─────┬──────┘ └──────┬──────┘ └──────┬──────┘
            │               │               │
       ┌────▼────┐    ┌─────▼─────┐    Kafka│
       │ Redis   │    │ PostgreSQL│    ┌─────▼─────┐
       │ :6379   │    │ :7002/:3  │    │ Kafka     │
       └─────────┘    │ (Floci)   │    │ (futuro)  │
                      └───────────┘    └───────────┘
```

### Servicios

| Servicio | Puerto | Base de datos | Responsabilidad |
| ---------- | -------- | --------------- | ----------------- |
| `gateway-service` | 8080 | Redis (sessions) | API edge, JWT validation, routing, migration filters |
| `identity-service` | 8081 | PostgreSQL (`etribunal_identity`) | Auth local, users, follows |
| `core-domain-service` | 8082 | PostgreSQL (`etribunal_core`) | Cases, votes, comments, reactions, media |
| `ai-engine-service` | 8083 | PostgreSQL (`etribunal_core`, shared) | AI automation, moderation |

### Libs compartidas

| Lib | Contenido |
| ----- | ----------- |
| `common-domain` | DTOs, eventos de dominio, excepciones, enums |
| `common-security` | JWT token provider (Nimbus JOSE) |
| `common-kafka` | Topic constants, serialización JSON |
| `common-test` | Testcontainers (Floci) |

## Requisitos

| Herramienta | Versión | Enlace de instalación |
| ------------- | --------- | ---------------------- |
| **JDK 21+** | 21 LTS | Gradle auto-provisiona Temurin 21 vía Foojay si difiere — [Descargar manual](https://adoptium.net/temurin/releases/?version=21) |
| **Docker Desktop** | 4.x+ | [Windows](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe) / [macOS](https://desktop.docker.com/mac/main/amd64/Docker.dmg) / [Linux](https://docs.docker.com/engine/install/) |
| **AWS CLI v2** | 2.x | [Windows](https://awscli.amazonaws.com/AWSCLIV2.msi) / [macOS](https://awscli.amazonaws.com/AWSCLIV2.pkg) / [Linux](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-linux.html) |
| **Floci** | latest | [Docker Hub](https://hub.docker.com/r/floci/floci) — `docker pull floci/floci:latest` |
| **PostgreSQL** | — | Via Floci (RDS emulator) — **NO** requiere instalación local |
| **Gradle** | 9.7+ | Wrapper incluido (`./gradlew`) — no requiere instalación |

> **Nota**: JDK 21 y Gradle se auto-gestionan vía el wrapper (`./gradlew`). Solo necesitas instalar **Docker Desktop**, **AWS CLI v2** y **Floci** manualmente.

---

## Primer arranque en desarrollo

Esta guía cubre desde cero hasta tener los 4 servicios corriendo con health checks verdes.

> **Modo Floci**: El proyecto usa **Floci compartido (EXTERNAL)** por defecto. Una sola instancia Floci sirve a todos tus proyectos locales. Ver [Instalación y configuración de Floci (solo primera vez)](#instalación-y-configuración-de-floci-solo-primera-vez) para la configuración inicial.
>
> **Variable de control**: `FLOCI_MODE` — `EXTERNAL` (default, Floci externo) | `DOCKER` (Floci en docker-compose profile `floci-local`).

### Paso 0: Clonar e instalar (aplica a ambos modos)

```bash
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build          # Compila todo + corre tests (primera vez)
```

### Instalación y configuración de Floci (solo primera vez — modo EXTERNAL)

Floci emula servicios AWS localmente (RDS, S3, Lambda, etc.). Se ejecuta **una sola instancia compartida** para todos tus proyectos.

#### 1. Instalar Floci

```bash
# Opción A: Docker (recomendado)
docker pull floci/floci:latest
docker run -d --name floci-shared \
  -p 4566:4566 -p 7001-7099:7001-7099 \
  floci/floci:latest

# Opción B: Docker Compose (si prefieres)
cat > docker-compose.floci.yml <<'EOF'
services:
  floci:
    image: floci/floci:latest
    container_name: floci-shared
    ports:
      - "4566:4566"
      - "7001-7099:7001-7099"
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:4566/_localstack/health"]
      interval: 5s
      timeout: 3s
      retries: 10
EOF
docker compose -f docker-compose.floci.yml up -d
```

#### 2. Verificar que Floci está healthy

```bash
docker logs -f floci-shared
# Esperar hasta ver: "Ready." o healthcheck passing
```

#### 3. Crear instancias RDS en Floci (para este proyecto)

> **Nota**: AWS CLI requiere `--region` aunque sea Floci. Usa `us-east-1` (cualquier región válida funciona).
>
> **Tip**: Para no repetir `--region us-east-1` en cada comando, configúralo una vez:
>
> ```bash
> export AWS_DEFAULT_REGION=us-east-1
> # o permanentemente:
> aws configure set default.region us-east-1
> ```
>
> **Credenciales dummy**: Floci no requiere credenciales reales, pero AWS CLI las exige. Configura credenciales dummy:
>
> ```bash
> export AWS_ACCESS_KEY_ID=test
> export AWS_SECRET_ACCESS_KEY=test
> export AWS_DEFAULT_REGION=us-east-1
> # O permanentemente:
> aws configure set aws_access_key_id test
> aws configure set aws_secret_access_key test
> aws configure set default.region us-east-1
> ```

> **Nota**: Las instancias tardan ~30-60s. Verifica con:
>
> ```bash
> aws --endpoint-url http://localhost:4566 rds describe-db-instances
> ```

#### 4. Aplicar migraciones Flyway (solo primera vez / tras cambios de schema)

```bash
./gradlew :services:identity-service:flywayMigrate -Pprofile=local
./gradlew :services:core-domain-service:flywayMigrate -Pprofile=local
```

> **Esto solo se hace una vez**. En arranques posteriores, Flyway detecta migraciones ya aplicadas y no hace nada.

---

### Opción A: Modo Externo (FLOCI_MODE=EXTERNAL) — **Por defecto**

Usa tu instancia Floci compartida. Requiere Floci corriendo externamente.

#### Pasos recurrentes (cada vez que inicies desarrollo)

```bash
# 1. Asegurar Floci corriendo
docker start floci-shared   # o docker compose -f docker-compose.floci.yml up -d

# 2. Levantar servicios (4 terminales separadas)
# Terminal 1: Gateway
./gradlew :services:gateway-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2: Identity
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'

# Terminal 3: Core Domain
./gradlew :services:core-domain-service:bootRun --args='--spring.profiles.active=local'

# Terminal 4: AI Engine (opcional)
./gradlew :services:ai-engine-service:bootRun --args='--spring.profiles.active=local'
```

> **Variables de entorno** (opcional, si tus puertos difieren):
>
> ```bash
> export FLOCI_MODE=EXTERNAL
> export FLOCI_HOST=localhost
> export FLOCI_IDENTITY_PORT=7002
> export FLOCI_CORE_PORT=7003
> ```

---

### Opción B: Modo Docker (FLOCI_MODE=DOCKER) — Fallback / CI / Onboarding

Incluye Floci en docker-compose del proyecto. Útil si no quieres configurar Floci aparte.

```bash
# 1. Clonar e instalar
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build          # Compila todo + corre tests (primera vez)

# 2. Construir jars
./gradlew bootJar

# 3. Levantar todo (infra + Floci + 4 servicios Spring)
FLOCI_MODE=docker docker compose --profile app --profile floci-local up -d

# Ver logs
docker compose logs -f gateway-service
docker compose logs -f identity-service
docker compose logs -f core-domain-service
docker compose logs -f ai-engine-service
```

> **Nota**: Este modo levanta un Floci **temporal** solo para este proyecto (profile `floci-local`). Los datos no persisten entre `docker compose down`.
```

> **Nota**: Este modo levanta un Floci **temporal** solo para este proyecto (profile `floci-local`). Los datos no persisten entre `docker compose down`.

```

---

### Verificación común

```bash
# Health checks
curl http://localhost:8080/actuator/health                    # Gateway
curl http://localhost:8081/api/actuator/health                # Identity
curl http://localhost:8082/api/actuator/health                # Core Domain
curl http://localhost:8083/actuator/health                    # AI Engine
```

**Respuesta esperada:** `{"status":"UP",...}`

---

### URLs útiles

| Servicio | Swagger UI | Health |
| ---------- | ------------ | -------- |
| Gateway | — | <http://localhost:8080/actuator/health> |
| Identity | <http://localhost:8081/api/swagger-ui.html> | <http://localhost:8081/api/actuator/health> |
| Core Domain | <http://localhost:8082/api/swagger-ui.html> | <http://localhost:8082/api/actuator/health> |
| AI Engine | <http://localhost:8083/swagger-ui.html> | <http://localhost:8083/actuator/health> |

---

## Docker Compose (referencia rápida)

```bash
# Solo infra (Redis)
docker compose up -d

# Infra + Floci (para modo docker)
docker compose --profile floci-local up -d

# Infra + 4 servicios Spring (modo externo, requiere Floci externo corriendo)
docker compose --profile app up -d

# Infra + Floci + 4 servicios Spring (modo docker, todo junto)
docker compose --profile app --profile floci-local up -d

# Infra + Temporal (para AI Engine workflows)
docker compose --profile temporal up -d

# Ver estado
docker compose ps

# Logs
docker compose logs -f <service-name>

# Parar todo
docker compose down
```

> **Prerequisito**: construir jars antes de levantar servicios Spring
>
> ```bash
> cd etribunal-platform && ./gradlew bootJar
> ```

---

## Correr tests

```bash
# Todos los tests (unit + integration)
./gradlew test

# Solo un servicio
./gradlew :services:identity-service:test

# E2E (requiere servicios corriendo)
./gradlew :tests:e2e:test -De2e.enabled=true
```

---

## Swagger UI

Disponible en cada servicio (deshabilitable vía `SPRINGDOC_SWAGGER_UI_ENABLED`):

| Servicio | URL |
| ---------- | ----- |
| Identity | <http://localhost:8081/api/swagger-ui.html> |
| Core Domain | <http://localhost:8082/api/swagger-ui.html> |
| AI Engine | <http://localhost:8083/swagger-ui.html> |

---

## Estructura del proyecto

```
etribunal-platform/
├── gradle/libs.versions.toml          # Catálogo central de versiones
├── settings.gradle.kts                 # Módulos incluidos
├── docker-compose.yml                  # Infra + servicios
├── libs/
│   ├── common-domain/                  # DTOs, eventos, excepciones
│   ├── common-security/                # JWT provider
│   ├── common-kafka/                   # Topics, serialización
│   └── common-test/                    # Testcontainers
├── services/
│   ├── gateway-service/                # Spring Cloud Gateway
│   ├── identity-service/               # Auth + Users
│   ├── core-domain-service/            # Cases + Domain
│   └── ai-engine-service/              # AI Automation
├── tests/
│   └── e2e/                            # End-to-end tests
└── docs/
    ├── adr/                            # Architecture Decision Records
    ├── API_REFERENCE.md                # Endpoints por servicio
    ├── ARCHITECTURE.md                 # Comunicación, flujo de datos
    ├── DEVELOPMENT.md                  # Guía de desarrollo local
    ├── SECURITY.md                     # JWT, auth, rate limiting
    ├── MIGRATION_STRATEGY.md           # Strangler Fig, shadow, canary
    └── DEPLOY.md                       # CI/CD, Docker, env vars
```

---

## GitFlow

- `main` → producción (tagged: v1.0.0, v1.1.0)
- `develop` → staging
- `feature/*` → features nuevas
- `release/*` → preparación de release
- `hotfix/*` → fixes urgentes

---

## Documentación

| Documento | Contenido |
| ----------- | ----------- |
| [API Reference](docs/API_REFERENCE.md) | Todos los endpoints por servicio |
| [Architecture](docs/ARCHITECTURE.md) | Comunicación entre servicios, flujo de datos |
| [Development](docs/DEVELOPMENT.md) | Setup local, debugging, Floci |
| [Security](docs/SECURITY.md) | JWT, auth, rate limiting |
| [Migration Strategy](docs/MIGRATION_STRATEGY.md) | Strangler Fig, shadow traffic, canary |
| [Deploy](docs/DEPLOY.md) | CI/CD, Docker, variables de entorno |
| [ADRs](docs/adr/) | Architecture Decision Records (001-009) |

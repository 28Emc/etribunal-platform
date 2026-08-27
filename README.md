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
|----------|--------|---------------|-----------------|
| `gateway-service` | 8080 | Redis (sessions) | API edge, JWT validation, routing, migration filters |
| `identity-service` | 8081 | PostgreSQL (`etribunal_identity`) | Auth local, users, follows |
| `core-domain-service` | 8082 | PostgreSQL (`etribunal_core`) | Cases, votes, comments, reactions, media |
| `ai-engine-service` | 8083 | PostgreSQL (`etribunal_core`, shared) | AI automation, moderation |

### Libs compartidas

| Lib | Contenido |
|-----|-----------|
| `common-domain` | DTOs, eventos de dominio, excepciones, enums |
| `common-security` | JWT token provider (Nimbus JOSE) |
| `common-kafka` | Topic constants, serialización JSON |
| `common-test` | Testcontainers (Floci) |

## Requisitos

- **JDK 21+** (Gradle auto-provisiona Temurin 21 vía Foojay si difiere)
- **Docker Desktop** (para Floci y Redis local)
- **PostgreSQL** via Floci (RDS emulator) — NO requiere instalación local

## Quickstart

```bash
# 1. Compilar todo
./gradlew build

# 2. Infra local
docker compose up -d                    # Floci + Redis

# 3. Levantar servicios (cada uno en terminal separada)
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:core-domain-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:gateway-service:bootRun --args='--spring.profiles.active=local'

# 4. Verificar
curl http://localhost:8080/actuator/health
```

### Docker Compose (servicios Spring)

```bash
# Construir jars primero
./gradlew bootJar

# Levantar infra + 4 servicios
docker compose --profile app up -d

# Solo infra (Floci + Redis)
docker compose up -d
```

### Correr tests

```bash
# Todos los tests (unit + integration)
./gradlew test

# Solo un servicio
./gradlew :services:identity-service:test

# E2E (requiere servicios corriendo)
./gradlew :tests:e2e:test -De2e.enabled=true
```

### Swagger UI

Disponible en cada servicio (deshabilitable vía `SPRINGDOC_SWAGGER_UI_ENABLED`):

| Servicio | URL |
|----------|-----|
| Identity | http://localhost:8081/api/swagger-ui.html |
| Core Domain | http://localhost:8082/api/swagger-ui.html |
| AI Engine | http://localhost:8083/swagger-ui.html |

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

## GitFlow

- `main` → producción (tagged: v1.0.0, v1.1.0)
- `develop` → staging
- `feature/*` → features nuevas
- `release/*` → preparación de release
- `hotfix/*` → fixes urgentes

## Documentación

| Documento | Contenido |
|-----------|-----------|
| [API Reference](docs/API_REFERENCE.md) | Todos los endpoints por servicio |
| [Architecture](docs/ARCHITECTURE.md) | Comunicación entre servicios, flujo de datos |
| [Development](docs/DEVELOPMENT.md) | Setup local, debugging, Floci |
| [Security](docs/SECURITY.md) | JWT, auth flow, internal tokens |
| [Migration Strategy](docs/MIGRATION_STRATEGY.md) | Strangler Fig, shadow traffic, canary |
| [Deploy](docs/DEPLOY.md) | CI/CD, Docker, variables de entorno |
| [ADRs](docs/adr/) | Architecture Decision Records (001-009) |

# etribunal-platform

Backend de **eTribunal** — plataforma microservicios (Java 21 + Spring Boot 3.5).

> Plan completo: [`../MICROSERVICES_MIGRATION.md`](../MICROSERVICES_MIGRATION.md)

## Estructura

```
libs/
├── common-domain      Eventos de dominio, DTOs, excepciones compartidas
├── common-security    JWT (nimbus), principal autenticado
├── common-kafka       Topics, serialización JSON de eventos
└── common-test        Testcontainers (Floci, PostgreSQL)

services/
├── gateway-service     Spring Cloud Gateway (:8080)
├── identity-service    Auth + Users (:8081)          ← Fase 1
├── core-domain-service Cases/Votes/Comments (:8082)  ← Fase 2
└── ai-engine-service   Automation/Moderación (:8083) ← Fase 3
```

## Requisitos

- JDK 21+ (toolchain auto-provisiona Temurin 21 vía Foojay)
- Docker Desktop (Floci local)

## Quickstart

```bash
# Compilar todo
./gradlew build

# Solo un servicio
./gradlew :services:identity-service:build

# Infra local (Floci + Temporal)
docker compose up -d

# Levantar identity-service en dev (requiere Floci corriendo)
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'
```

## GitFlow

- `main` → producción (protected)
- `develop` → staging (protected)
- `feature/*`, `release/*`, `hotfix/*`

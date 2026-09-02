# eTribunal Platform

Backend de microservicios de **eTribunal** — Java 21 + Spring Boot 3.5 + Gradle monorepo.

| | |
| --- | --- |
| Gateway | `:8080` (Spring Cloud Gateway) |
| Identity | `:8081` (Auth + Users) |
| Core Domain | `:8082` (Cases + Votes + Comments + Media) |
| AI Engine | `:8083` (Automation + Moderación) |
| Explore | [Swagger UI](#swagger-ui) · [Documentación](#documentación) |

---

## Arquitectura

```
                              ┌─────────────┐
                              │   Gateway   │ :8080   (Spring Cloud Gateway, JWT filter)
                              └──────┬──────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                │
            ┌───────▼──────┐ ┌───────▼──────┐ ┌──────▼───────┐
            │   Identity   │ │ Core Domain  │ │  AI Engine   │
            │    :8081     │ │    :8082     │ │    :8083     │
            │ Auth / Users │ │ Cases / Votes│ │ Automation / │
            │ Follows      │ │ Comments /   │ │ Moderación   │
            │              │ │ Media        │ │              │
            └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
                   │                │                │
         ┌─────────▼────┐   ┌───────▼────────┐   ┌───▼──────────┐
         │    Redis     │   │ PostgreSQL     │   │    Kafka     │
         │    :6379     │   │ :7002 / :7003  │   │   :9092      │
         │  (sesión)    │   │   (Floci RDS)  │   │  (eventos)   │
         └──────────────┘   └────────────────┘   └──────────────┘
```

### Servicios

| Servicio | Puerto | Depende de | Responsabilidad |
| ---------- | -------- | ----------- | ----------------- |
| `gateway-service` | 8080 | Redis | API edge, validación JWT, routing |
| `identity-service` | 8081 | PostgreSQL `etribunal_identity`, Redis | Auth local, usuarios, follows |
| `core-domain-service` | 8082 | PostgreSQL `etribunal_core`, Redis, Kafka*, S3 (Floci) | Casos, votos, comentarios, reacciones, media |
| `ai-engine-service` | 8083 | PostgreSQL `etribunal_core` (shared), Kafka* | Automatización IA, moderación |

> **\*Kafka es best-effort**: si el broker no está disponible, el sistema sigue funcionando (la producción de eventos no bloquea las requests). Kafka se necesita solo si pruebas flujos de eventos de media (core) o automatización (ai-engine). Ver [Qué se necesita levantar](#qué-se-necesita-levantar).

### Librerías compartidas

| Lib | Contenido |
| ----- | ----------- |
| `common-domain` | DTOs, eventos de dominio, excepciones, enums |
| `common-security` | Proveedor de tokens JWT (Nimbus JOSE) |
| `common-kafka` | Constantes de topics, serialización JSON |
| `common-test` | Testcontainers (Floci) |

---

## Requisitos

| Herramienta | Req | Nota |
| ------------- | ----- | ------ |
| **JDK 21+** | 21 LTS | Auto-provisionado vía wrapper (Foojay) |
| **Docker Desktop** | 4.x+ | Necesario para Redis + Floci + Kafka + Zipkin |
| **AWS CLI v2** | 2.x | Para administrar Floci (RDS, S3) |
| **Gradle** | 9.7+ | Wrapper incluido (`./gradlew`) |
| **PostgreSQL** | — | NO local; vía Floci (emulador RDS) |

> Solo necesitas instalar manualmente **Docker Desktop** y **AWS CLI v2**. JDK/Gradle se auto-gestionan.

---

## Guía de arranque local (modo externo, recomendado)

El flujo de desarrollo se resume en **4 pasos**:

1. **Crear las bases RDS en Floci** (una sola vez).
2. **Levantar la infra** (Redis + Floci + Zipkin + Kafka + S3).
3. **Aplicar migraciones Flyway** (una sola vez / tras cambios de schema).
4. **Levantar los 4 servicios** + la UI, y verificar.

Los pasos 2 y 4 están automatizados en scripts; los pasos 1 y 3 son setup inicial.

### Paso 1 — Bases RDS en Floci (solo primera vez)

> Requiere Floci corriendo (ver Paso 2) y credenciales dummy AWS configuradas:
>
> ```bash
> aws configure set aws_access_key_id test
> aws configure set aws_secret_access_key test
> aws configure set default.region us-east-1
> ```

Crea las dos instancias RDS que emulan los PostgreSQL de identity y core:

```bash
# Identity DB (puerto 7002)
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-identity-local \
  --db-name etribunal_identity \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20

# Core DB (puerto 7003)
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-core-local \
  --db-name etribunal_core \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20
```

> Las instancias tardan ~30-60s en estar listas. Verifica con `aws --endpoint-url http://localhost:4566 rds describe-db-instances`.
>
> Si ya existen (`DBInstanceAlreadyExists`), **salta este paso**. Para recrearlas, bórralas primero:
> `aws --endpoint-url http://localhost:4566 rds delete-db-instance --db-instance-identifier etribunal-identity-local --skip-final-snapshot` (y análogo para `etribunal-core-local`).

### Qué se necesita levantar

| Componente | ¿Necesario? | Puerto | Por qué / Cuándo |
| ----------- | ------------- | -------- | ------------------ |
| **Redis** | ✅ **Sí** | `:6379` | Sesiones/caché (gateway, identity, core). **Sin él el login falla.** |
| **Floci** | ✅ **Sí** | `:4566`, RDS `:7001-7099` | Emula PostgreSQL (identity `:7002`, core `:7003`) y S3. |
| **Bucket S3 `etribunal-media`** | ✅ Sí* | vía Floci `:4566` | Subida de avatar/imágenes. *Solo si pruebas media* — lo crea el script. |
| **Zipkin** | ⚠️ Recomendado | `:9411` | Tracing distribuido. Si no está, los servicios reportan best-effort (no rompe). |
| **Kafka** | ⚠️ Recomendado* | `:9092` | Eventos de media (core) y automatización (ai-engine). Best-effort; *necesario solo si pruebas esos flujos*. |
| **Temporal** | ❌ Opt-in | `:7233/:8233` | Workflows de IA. Solo si pruebas automatización con Temporal. |

> **Mínimo funcional**: Redis + Floci (+ bucket S3 si pruebas media). El resto mejora observabilidad/casos de uso, pero no es bloqueante para la app base.

### Paso 2 — Levantar la infra

**Windows (script, recomendado)** — levanta Redis + Floci (+RDS/S3) + Zipkin + Kafka:

```bat
scripts\infra-up.bat            :: Redis + Floci + Zipkin + Kafka + bucket S3
scripts\infra-up.bat temporal   :: + Temporal (opt-in, workflows de IA)
```

El script es **idempotente**: reusa Floci si ya hay una instancia en `:4566`, salta lo que ya corre y asegura el bucket S3.

**Manual (cualquier SO)**:

```bash
# Redis (obligatorio)
docker compose --profile floci-local up -d    # incluye Floci + floci-init (crea bucket S3)
docker compose up -d redis

# Infra complementaria
docker compose --profile zipkin up -d         # Zipkin tracing :9411
docker compose --profile kafka up -d          # Kafka KRaft :9092
docker compose --profile temporal up -d       # temporal (opcional)
```

> Nota: `floci-init` crea el bucket S3 `etribunal-media` automáticamente contra Floci (idempotente). Manual: `aws --endpoint-url http://localhost:4566 s3 mb s3://etribunal-media`.

### Paso 3 — Aplicar migraciones Flyway (solo primera vez / tras cambios de schema)

```bash
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate
```

> Las tareas apuntan por defecto a `localhost:7002` (identity) y `localhost:7003` (core). Si tus puertos difieren, override con system properties JVM:
> `./gradlew :services:identity-service:flywayMigrate -DFLOCI_HOST=localhost -DFLOCI_IDENTITY_PORT=7002`
> En arranques posteriores Flyway detecta lo ya aplicado y no hace nada. (El arranque con perfil `local` también aplica Flyway automáticamente.)

### Paso 4 — Levantar los 4 servicios

**Desde VS Code (tasks)** — ejecuta `Tasks: Run Task`:

- `Start eTribunal infra (Redis + Floci + Zipkin + Kafka + S3)` — Paso 2
- `Start eTribunal projects` — los 4 servicios + UI

**Windows (scripts, 4 ventanas)**:

```bat
scripts\start-gateway.bat        :: :8080
scripts\start-identity.bat       :: :8081
scripts\start-core.bat           :: :8082
scripts\start-ai.bat             :: :8083
```

**Manual (4 terminales)**:

```bash
./gradlew :services:gateway-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:core-domain-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:ai-engine-service:bootRun --args='--spring.profiles.active=local'
```

> El perfil `local` incluye los secrets de desarrollo y las rutas de gateway (`on-profile: local`).
>
> **AI Engine (modo externo)**: por defecto apunta a `localhost:7003`/`localhost:9092`. Si difiere:
>
> ```bash
> export CORE_DB_HOST=localhost ; export CORE_DB_PORT=7003
> export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
> ```

### Verificación

```bash
curl http://localhost:8080/actuator/health       # Gateway
curl http://localhost:8081/api/actuator/health   # Identity
curl http://localhost:8082/api/actuator/health   # Core Domain
curl http://localhost:8083/actuator/health       # AI Engine
```

Respuesta esperada: `{"status":"UP",...}`.

### URLs útiles

| Servicio | Swagger UI | Health |
| ---------- | ------------ | -------- |
| Gateway | — | `http://localhost:8080/actuator/health` |
| Identity | `http://localhost:8081/api/swagger-ui.html` | `http://localhost:8081/api/actuator/health` |
| Core Domain | `http://localhost:8082/api/swagger-ui.html` | `http://localhost:8082/api/actuator/health` |
| AI Engine | `http://localhost:8083/swagger-ui.html` | `http://localhost:8083/actuator/health` |
| Zipkin | `http://localhost:9411/zipkin/` | `http://localhost:9411/health` |

---

## Docker Compose completo (modo docker) — fallback / CI

Levanta infra + los 4 servicios Spring **en contenedores** mediante los `docker/Dockerfile`:

```bash
# 1. Clonar e instalar (primera vez)
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew bootJar                        # construir fat-jars

# 2. Levantar todo (infra + Floci + 4 servicios Spring)
FLOCI_MODE=docker docker compose --profile app --profile floci-local up -d

# 3. Opcionales
docker compose --profile zipkin up -d    # Zipkin :9411
docker compose --profile kafka up -d     # Kafka :9092

# 4. Migraciones Flyway (Floci en docker es fresco cada vez)
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate

# Ver logs
docker compose logs -f <service-name>
```

> **Nota**: este modo levanta un Floci **temporal** solo para este proyecto (profile `floci-local`). Los datos no persisten entre `docker compose down`.

---

## Correr tests

```bash
./gradlew test                              # Todos (unit + integration)
./gradlew :services:identity-service:test   # Solo un servicio
./gradlew :tests:e2e:test -De2e.enabled=true   # E2E (requiere servicios corriendo)
```

---

## Swagger UI

Disponible en cada servicio (deshabilitable vía `SPRINGDOC_SWAGGER_UI_ENABLED`):

| Servicio | URL |
| ---------- | ----- |
| Identity | `http://localhost:8081/api/swagger-ui.html` |
| Core Domain | `http://localhost:8082/api/swagger-ui.html` |
| AI Engine | `http://localhost:8083/swagger-ui.html` |

---

## Estructura del proyecto

```
etribunal-platform/
├── gradle/libs.versions.toml          # Catálogo central de versiones
├── settings.gradle.kts                 # Módulos incluidos
├── docker-compose.yml                  # Infra + servicios
├── scripts/
│   ├── infra-up.bat                    # Levanta infra (Redis + Floci + Zipkin + Kafka + S3)
│   ├── start-gateway.bat               # bootRun gateway (:8080)
│   ├── start-identity.bat              # bootRun identity (:8081)
│   ├── start-core.bat                  # bootRun core (:8082)
│   └── start-ai.bat                    # bootRun ai-engine (:8083)
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
└── docs/                               # documentación (ver abajo)
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

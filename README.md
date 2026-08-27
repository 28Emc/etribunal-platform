# eTribunal Platform

Backend microservicios de **eTribunal** â€” Java 21 + Spring Boot 3.5 + Gradle monorepo.

## Arquitectura

```
                    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
                    â”‚  Gateway    â”‚ :8080 (Spring Cloud Gateway)
                    â”‚  JWT filter â”‚
                    â””â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”˜
                           â”‚
               â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
               â”‚            â”‚            â”‚
      â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â” â”Œâ”€â”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”€â” â”Œâ”€â”€â–¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
      â”‚ Identity   â”‚ â”‚ Core Domain â”‚ â”‚  AI Engine   â”‚
      â”‚ :8081      â”‚ â”‚ :8082       â”‚ â”‚  :8083       â”‚
      â”‚ Auth/Users â”‚ â”‚ Cases/Votes â”‚ â”‚  Automation  â”‚
      â””â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”˜ â””â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”˜ â””â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”˜
            â”‚               â”‚               â”‚
       â”Œâ”€â”€â”€â”€â–¼â”€â”€â”€â”€â”    â”Œâ”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”    Kafkaâ”‚
       â”‚ Redis   â”‚    â”‚ PostgreSQLâ”‚    â”Œâ”€â”€â”€â”€â”€â–¼â”€â”€â”€â”€â”€â”
       â”‚ :6379   â”‚    â”‚ :7002/:3  â”‚    â”‚ Kafka     â”‚
       â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â”‚ (Floci)   â”‚    â”‚ (futuro)  â”‚
                      â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
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
| `common-kafka` | Topic constants, serializaciÃ³n JSON |
| `common-test` | Testcontainers (Floci) |

## Requisitos

| Herramienta | VersiÃ³n | Enlace de instalaciÃ³n |
| ------------- | --------- | ---------------------- |
| **JDK 21+** | 21 LTS | Gradle auto-provisiona Temurin 21 vÃ­a Foojay si difiere â€” [Descargar manual](https://adoptium.net/temurin/releases/?version=21) |
| **Docker Desktop** | 4.x+ | [Windows](https://desktop.docker.com/win/main/amd64/Docker%20Desktop%20Installer.exe) / [macOS](https://desktop.docker.com/mac/main/amd64/Docker.dmg) / [Linux](https://docs.docker.com/engine/install/) |
| **AWS CLI v2** | 2.x | [Windows](https://awscli.amazonaws.com/AWSCLIV2.msi) / [macOS](https://awscli.amazonaws.com/AWSCLIV2.pkg) / [Linux](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-linux.html) |
| **Floci** | latest | [Docker Hub](https://hub.docker.com/r/floci/floci) â€” `docker pull floci/floci:latest` |
| **PostgreSQL** | â€” | Via Floci (RDS emulator) â€” **NO** requiere instalaciÃ³n local |
| **Gradle** | 9.7+ | Wrapper incluido (`./gradlew`) â€” no requiere instalaciÃ³n |

> **Nota**: JDK 21 y Gradle se auto-gestionan vÃ­a el wrapper (`./gradlew`). Solo necesitas instalar **Docker Desktop**, **AWS CLI v2** y **Floci** manualmente.

---

## Primer arranque en desarrollo

Esta guÃ­a cubre desde cero hasta tener los 4 servicios corriendo con health checks verdes.

> **Modo Floci**: El proyecto usa **Floci compartido (EXTERNAL)** por defecto. Una sola instancia Floci sirve a todos tus proyectos locales. Ver [InstalaciÃ³n y configuraciÃ³n de Floci (solo primera vez)](#instalaciÃ³n-y-configuraciÃ³n-de-floci-solo-primera-vez) para la configuraciÃ³n inicial.
>
> **Variable de control**: `FLOCI_MODE` â€” `EXTERNAL` (default, Floci externo) | `DOCKER` (Floci en docker-compose profile `floci-local`).

### Paso 0: Clonar e instalar (aplica a ambos modos)

```bash
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build          # Compila todo + corre tests (primera vez)
```

### InstalaciÃ³n y configuraciÃ³n de Floci (solo primera vez â€” modo EXTERNAL)

Floci emula servicios AWS localmente (RDS, S3, Lambda, etc.). Se ejecuta **una sola instancia compartida** para todos tus proyectos.

#### 1. Instalar Floci

```bash
# OpciÃ³n A: Docker (recomendado)
docker pull floci/floci:latest
docker run -d --name floci-shared \
  -p 4566:4566 -p 7001-7099:7001-7099 \
  floci/floci:latest

# OpciÃ³n B: Docker Compose (si prefieres)
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

#### 2. Verificar que Floci estÃ¡ healthy

```bash
docker logs -f floci-shared
# Esperar hasta ver: "Ready." o healthcheck passing
```

#### 3. Crear instancias RDS en Floci (para este proyecto)

> **Nota**: AWS CLI requiere `--region` aunque sea Floci. Usa `us-east-1` (cualquier regiÃ³n vÃ¡lida funciona).
>
> **Tip**: Para no repetir `--region us-east-1` en cada comando, configÃºralo una vez:
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

 
#### 4. Crear instancias RDS en Floci (para este proyecto)
 
`ash
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
`
 
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

### OpciÃ³n A: Modo Externo (FLOCI_MODE=EXTERNAL) â€” **Por defecto**

Usa tu instancia Floci compartida. Requiere Floci corriendo externamente.

#### Pasos recurrentes (cada vez que inicies desarrollo)

```bash
# 1. Asegurar Floci corriendo y healthy
docker start floci-shared   # o docker compose -f docker-compose.floci.yml up -d
# Esperar healthcheck: docker logs -f floci-shared (esperar "Ready.")

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

### OpciÃ³n B: Modo Docker (FLOCI_MODE=DOCKER) â€” Fallback / CI / Onboarding

Incluye Floci en docker-compose del proyecto. Ãštil si no quieres configurar Floci aparte.

```bash
# 1. Clonar e instalar
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build          # Compila todo + corre tests (primera vez)

# 2. Construir jars
./gradlew bootJar

# 3. Levantar todo (infra + Floci + 4 servicios Spring)
FLOCI_MODE=docker docker compose --profile app --profile floci-local up -d

# 4. Aplicar migraciones Flyway (Floci en docker es fresco cada vez)
./gradlew :services:identity-service:flywayMigrate -Pprofile=local
./gradlew :services:core-domain-service:flywayMigrate -Pprofile=local

# 5. Crear bucket S3 para media (solo primera vez)
aws --endpoint-url http://localhost:4566 s3 mb s3://etribunal-media

# Ver logs
docker compose logs -f gateway-service
docker compose logs -f identity-service
docker compose logs -f core-domain-service
docker compose logs -f ai-engine-service
```

> **Nota**: Este modo levanta un Floci **temporal** solo para este proyecto (profile `floci-local`). Los datos no persisten entre `docker compose down`.
```

---

### VerificaciÃ³n comÃºn

```bash
# Health checks
curl http://localhost:8080/actuator/health                    # Gateway
curl http://localhost:8081/api/actuator/health                # Identity
curl http://localhost:8082/api/actuator/health                # Core Domain
curl http://localhost:8083/actuator/health                    # AI Engine
```

**Respuesta esperada:** `{"status":"UP",...}`

---

### URLs Ãºtiles

| Servicio | Swagger UI | Health |
| ---------- | ------------ | -------- |
| Gateway | â€” | <http://localhost:8080/actuator/health> |
| Identity | <http://localhost:8081/api/swagger-ui.html> | <http://localhost:8081/api/actuator/health> |
| Core Domain | <http://localhost:8082/api/swagger-ui.html> | <http://localhost:8082/api/actuator/health> |
| AI Engine | <http://localhost:8083/swagger-ui.html> | <http://localhost:8083/actuator/health> |

---

## Docker Compose (referencia rÃ¡pida)

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

Disponible en cada servicio (deshabilitable vÃ­a `SPRINGDOC_SWAGGER_UI_ENABLED`):

| Servicio | URL |
| ---------- | ----- |
| Identity | <http://localhost:8081/api/swagger-ui.html> |
| Core Domain | <http://localhost:8082/api/swagger-ui.html> |
| AI Engine | <http://localhost:8083/swagger-ui.html> |

---

## Estructura del proyecto

```
etribunal-platform/
â”œâ”€â”€ gradle/libs.versions.toml          # CatÃ¡logo central de versiones
â”œâ”€â”€ settings.gradle.kts                 # MÃ³dulos incluidos
â”œâ”€â”€ docker-compose.yml                  # Infra + servicios
â”œâ”€â”€ libs/
â”‚   â”œâ”€â”€ common-domain/                  # DTOs, eventos, excepciones
â”‚   â”œâ”€â”€ common-security/                # JWT provider
â”‚   â”œâ”€â”€ common-kafka/                   # Topics, serializaciÃ³n
â”‚   â””â”€â”€ common-test/                    # Testcontainers
â”œâ”€â”€ services/
â”‚   â”œâ”€â”€ gateway-service/                # Spring Cloud Gateway
â”‚   â”œâ”€â”€ identity-service/               # Auth + Users
â”‚   â”œâ”€â”€ core-domain-service/            # Cases + Domain
â”‚   â””â”€â”€ ai-engine-service/              # AI Automation
â”œâ”€â”€ tests/
â”‚   â””â”€â”€ e2e/                            # End-to-end tests
â””â”€â”€ docs/
    â”œâ”€â”€ adr/                            # Architecture Decision Records
    â”œâ”€â”€ API_REFERENCE.md                # Endpoints por servicio
    â”œâ”€â”€ ARCHITECTURE.md                 # ComunicaciÃ³n, flujo de datos
    â”œâ”€â”€ DEVELOPMENT.md                  # GuÃ­a de desarrollo local
    â”œâ”€â”€ SECURITY.md                     # JWT, auth, rate limiting
    â”œâ”€â”€ MIGRATION_STRATEGY.md           # Strangler Fig, shadow, canary
    â””â”€â”€ DEPLOY.md                       # CI/CD, Docker, env vars
```

---

## GitFlow

- `main` â†’ producciÃ³n (tagged: v1.0.0, v1.1.0)
- `develop` â†’ staging
- `feature/*` â†’ features nuevas
- `release/*` â†’ preparaciÃ³n de release
- `hotfix/*` â†’ fixes urgentes

---

## DocumentaciÃ³n

| Documento | Contenido |
| ----------- | ----------- |
| [API Reference](docs/API_REFERENCE.md) | Todos los endpoints por servicio |
| [Architecture](docs/ARCHITECTURE.md) | ComunicaciÃ³n entre servicios, flujo de datos |
| [Development](docs/DEVELOPMENT.md) | Setup local, debugging, Floci |
| [Security](docs/SECURITY.md) | JWT, auth, rate limiting |
| [Migration Strategy](docs/MIGRATION_STRATEGY.md) | Strangler Fig, shadow traffic, canary |
| [Deploy](docs/DEPLOY.md) | CI/CD, Docker, variables de entorno |
| [ADRs](docs/adr/) | Architecture Decision Records (001-009) |




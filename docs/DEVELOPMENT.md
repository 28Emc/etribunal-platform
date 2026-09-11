# Development Guide

> **¿Querés correr todo en 2 pasos?** (`scripts\docker-up.bat`) → mirá la sección
> [Docker Compose](#docker-compose) más abajo. El resto de este guide cubre el **modo externo**
> (Floci/Redis standalone + bootRun), que sigue disponible pero es más manual.

## Setup local

### Prerrequisitos

```bash
# Verificar JDK
java -version   # JDK 21+ (Gradle auto-provisiona Temurin 21 si difiere)

# Verificar Docker
docker --version   # Docker Desktop corriendo
```

### 1. Clonar e instalar

```bash
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build   # Compila todo + corre tests
```

### 2. Infraestructura local

**Windows (script, recomendado)**:

```bat
scripts\infra-up.bat            :: Redis + Floci + Zipkin + Kafka + bucket S3
scripts\infra-up.bat temporal   :: + Temporal (opt-in)
```

**Manual**:

```bash
docker compose --profile floci-local up -d   # Floci (:4566, RDS :7001-7099) + floci-init (bucket S3)
docker compose up -d redis                   # Redis :6379
docker compose --profile zipkin up -d        # Zipkin :9411 (opcional)
docker compose --profile app up -d kafka     # Kafka :9092 (vive en profile app)
```

> `docker compose up -d` (sin profile) solo levanta **Redis** — Floci está en el profile `floci-local`. El bucket S3 `etribunal-media` lo crea `floci-init` (idempotente).

### 3. Crear instancias RDS en Floci

> **En modo Docker no hace falta este paso**: `floci-init` (profile `floci-local`) crea las instancias
> y el bucket S3 automáticamente y es idempotente. Este paso aplica solo al **modo externo**
> (Floci standalone del host + bootRun).

> **Credenciales dummy**: Floci no requiere credenciales reales, pero AWS CLI las exige. Configura credenciales dummy:
> ```bash
> export AWS_ACCESS_KEY_ID=test
> export AWS_SECRET_ACCESS_KEY=test
> export AWS_DEFAULT_REGION=us-east-1
> # O permanentemente:
> aws configure set aws_access_key_id test
> aws configure set aws_secret_access_key test
> aws configure set default.region us-east-1
> ```

```bash
# Identity DB
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-identity-local \
  --db-name etribunal_identity \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20

# Core DB
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-core-local \
  --db-name etribunal_core \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20
```

> Las instancias tardan ~30s en estar disponibles. Los puertos son 7002 (identity) y 7003 (core).

### 4. Aplicar migraciones

Usa las tareas Gradle (apuntan por defecto a `localhost:7002`/`localhost:7003`):

```bash
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate
```

> Si tus puertos difieren, override con system properties JVM:
> `./gradlew :services:identity-service:flywayMigrate -DFLOCI_HOST=localhost -DFLOCI_IDENTITY_PORT=7002`
> El arranque con perfil `local` también aplica Flyway automáticamente.

### 5. Levantar servicios

Abrir 4 terminales separadas:

```bash
# Terminal 1: Gateway
./gradlew :services:gateway-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2: Identity
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'

# Terminal 3: Core Domain
./gradlew :services:core-domain-service:bootRun --args='--spring.profiles.active=local'

# Terminal 4: AI Engine (opcional)
./gradlew :services:ai-engine-service:bootRun --args='--spring.profiles.active=local'
```

### 6. Verificar

```bash
# Health checks
curl http://localhost:8080/actuator/health       # Gateway
curl http://localhost:8081/api/actuator/health   # Identity
curl http://localhost:8082/api/actuator/health   # Core Domain
curl http://localhost:8083/actuator/health       # AI Engine (opcional)

# Registrar usuario
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testjudge","email":"test@test.com","password":"Test1234!","displayName":"Test"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!"}'
```

## Docker Compose

### Modo todo-en-docker (recomendado en Windows)

> **⚠️ Alcance:** este modo (`docker-compose.yml`, perfiles `app`/`floci-local`, persistencia con
> volúmenes y seeds automáticos V5/V6) es **exclusivamente para desarrollo/prueba en local**.
> Producción se despliega y configura por otra vía (aun no documentada en este repo).

Un solo script compila los jars, construye las imágenes y levanta **todo** (Floci + Redis +
Kafka + 4 servicios Spring + UI):

```bat
scripts\docker-up.bat        :: up (app + floci-local)
scripts\docker-up.bat all    :: + Zipkin (tracing)
scripts\docker-down.bat      :: down (conserva los datos)
```

Manual (cualquier SO):

```bash
./gradlew bootJar                                 # fat-jars (obligatorio, los Dockerfiles copian el jar)
docker compose --profile app --profile floci-local up -d --build
```

> **Persistencia**: Floci corre con `FLOCI_STORAGE_MODE=hybrid` y el volumen `floci-data`
> montado en `/app/data`. La metadata de Floci (instancias RDS, buckets, etc.) y cada PostgreSQL
> hermano (`floci-rds-{volumeId}`) persisten entre `down`/`up`. `floci-init` es idempotente.
> Reset total: `docker compose --profile app --profile floci-local down -v`.

> **Seeds automáticos (identity, migraciones Flyway al arrancar):**
> - `V5__seed_admin.sql` → admin `admin@etribunal.com / Admin@2026` (ADMIN).
> - `V6__seed_bots.sql` → 25 bots para el pool del AI Engine.
>
> Si ya tenés una BD con migraciones anteriores, al subir identity se aplican V5/V6 encima
> (no duplican).

### Perfiles

```bash
docker compose up -d                                  # Solo Redis (infra base)
docker compose --profile floci-local up -d            # + Floci (RDS :7001-7099 + S3)
docker compose --profile app up -d                    # + 4 servicios Spring
docker compose --profile zipkin up -d                 # + Zipkin
docker compose --profile temporal up -d                # + Temporal + UI
docker compose --profile app --profile floci-local up -d  # Todo (modo docker)
```

### Construir servicios

```bash
./gradlew bootJar                              # Compila todos los servicios
docker compose --profile app up -d --build     # Reconstruye y levanta (rebuild de imágenes)
```

> Nota: los `Dockerfile` de los servicios **no compilan dentro** de la imagen: copian el fat-jar
> ya construido. Por eso cualquier cambio de código (incluidas migraciones Flyway nuevas) exige
> re-ejecutar `./gradlew <servicio>:bootJar` antes de `docker compose build <servicio>`.
> `scripts\docker-up.bat` hace ambos por vos.

### Observabilidad (Grafana LGTM: Prometheus + Tempo + Loki + Alloy)

Stack opcional de telemetría y logs centralizados, activado con un overlay sobre el compose
base (profile `observability`). Un solo script hace todo:

```bat
scripts\observability-up.bat            :: docker-up + Prometheus/Grafana/Loki/Tempo/Alloy
```

Manual:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml \
  --profile app --profile floci-local --profile observability up -d --build
```

**Qué hace sobre las apps** (sin el overlay el comportamiento queda igual que siempre):

| Ajuste | Valor con overlay | Sin overlay |
|---|---|---|
| Logs | JSON estructurado (`LOGGING_STRUCTURED_FORMAT=ecs`, incluye `trace.id`/`span.id`) | texto plano |
| Trazas | a Tempo vía Zipkin v2 (`MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://tempo:9411`) | Zipkin solo en profile `zipkin` |

| Componente | URL | Notas |
|---|---|---|
| Grafana | http://localhost:3001 | `admin/admin`, datasources auto-configurados |
| Prometheus | http://localhost:9090 | scrape de los 4 servicios (`/actuator/prometheus`) |
| Tempo | http://localhost:3200/search | receiver Zipkin en `:9411` |
| Loki | http://localhost:3100 | logs JSON de los contenedores vía Alloy |
| Alloy | http://localhost:12345 | grafana/Alloy UI |

**Verificar rápido** tras levantar:

1. Generar tráfico: login → crear caso → votar/comentar (y opcional un `POST /automation/run?dryRun=true`).
2. Grafana → **Explore** → Prometheus: buscar `jvm_memory_used_bytes` o `http_server_requests_seconds_count`.
3. **Explore** → Tempo: buscar un service (`gateway-service`, `core-domain-service`, ...) en los últimos 15 min.
4. **Explore** → Loki: filtar `{container_name=~"etribunal-.*"}`; cada línea JSON trae `trace.id` → click para abrir la traza.

> **Backend**: agregamos `micrometer-registry-prometheus` a los 4 servicios y el knob
> `logging.structured.format.console` (env `LOGGING_STRUCTURED_FORMAT`). Dashboard de ejemplo:
> Grafana → Dashboards → New → import → `19004` (JVM/Micrometer Spring Boot).

## Testing

### Unit + Integration tests

```bash
./gradlew test                                 # Todos
./gradlew :services:identity-service:test      # Solo identity
./gradlew :services:core-domain-service:test   # Solo core
```

### E2E tests

Los E2E tests hacen llamadas HTTP reales a servicios corriendo:

```bash
# Terminal 1: Levantar servicios
docker compose --profile app up -d

# Terminal 2: Correr E2E
./gradlew :tests:e2e:test -De2e.enabled=true -Dgateway.url=http://localhost:8080
```

### Test profiles

Cada servicio tiene `application-test.yml` que usa H2 in-memory:

```bash
# Tests con H2 (sin Floci)
./gradlew :services:identity-service:test -Dspring.profiles.active=test
```

## Debugging

### IntelliJ IDEA

1. Run → Edit Configurations → Gradle
2. Task: `:services:identity-service:bootRun`
3. Arguments: `--args='--spring.profiles.active=local'`
4. Activate: ✅ Override parameters → `spring.profiles.active=local`

### VS Code

```bash
# Con Java Extension Pack instalado
# Abrir la carpeta del servicio específico
# F5 → Launch: Spring Boot

# O manualmente con breakpoints
./gradlew :services:identity-service:bootRun \
  --args='--spring.profiles.active=local' \
  -Dorg.gradle.jvmargs='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'
```

### Logs

```bash
# Ver logs en tiempo real (cuando corren via bootRun)
# Los logs salen en la terminal de bootRun

# Logging levels configurables en application-local.yml
logging:
  level:
    com.etribunal: DEBUG
    org.springframework.security: DEBUG
```

## Hot Reload

Spring Boot DevTools no está configurado (no es recomendado en monorepos Gradle). Alternativas:

- **bootRun**: Se reinicia automáticamente al detectar cambios en `src/main/java`
- **IntelliJ**: Build → Build Project (Ctrl+F9) + Enable Auto-Compile
- **VS Code**: Java Language Server auto-compila

## Floci (LocalStack)

### Comandos útiles

```bash
# Listar instancias RDS
aws --endpoint-url http://localhost:4566 rds describe-db-instances

# Listar buckets S3
aws --endpoint-url http://localhost:4566 s3 ls

# Crear bucket de media
aws --endpoint-url http://localhost:4566 s3 mb s3://etribunal-media

# Verificar salud
curl http://localhost:4566/_localstack/health
```

### Credenciales

| Variable | Valor |
|----------|-------|
| `AWS_ACCESS_KEY_ID` | `test` |
| `AWS_SECRET_ACCESS_KEY` | `test` |
| `AWS_REGION` | `us-east-1` |
| `S3_ENDPOINT` | `http://localhost:4566` |

## Troubleshooting (Docker)

| Síntoma | Causa raíz | Solución |
|---|---|---|
| Kafka no arranca: `KAFKA-18281 ... nonroutable meta-address 0.0.0.0` | listeners con `0.0.0.0` explícito (bug en 3.9.0) | En `docker-compose.yml` usar bind implícito: `KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093` (sin `0.0.0.0`) |
| `UnknownHostException: floci` en los backends | el servicio quedó en la red vieja del proyecto (cambio de nombre a `etribunal-net`) | `docker compose --profile app up -d --force-recreate` para re-crear en la red nueva |
| UI no renderiza / `Could not resolve ...rolldown binding` | `COPY . .` del Dockerfile copió los `node_modules` Windows por encima del Linux | agregar/verificar `etribunal-ui/.dockerignore` (excluye `node_modules`, `dist`) y rebuild de la imagen |
| Gateway responde `500 Connection refused: localhost:8081` | las rutas del compose apuntaban a `localhost` (hostnames internos) | el compose inyecta `IDENTITY_URL` / `CORE_URL` / `AI_URL` (hostnames de servicios); en `application.yml` el default local solo se usa en bootRun externo |
| `Spans were dropped ... ConnectException` en logs | Zipkin no está corriendo (tracing best-effort) | no es fatal; levantar con `scripts\docker-up.bat all` / profile `zipkin` si querés trazas |
| `BackoffRestart` de identity/core con `connection refused floci:700x` | Floci todavía no terminó de crear las RDS / no estaban disponibles | normal los primeros ~30-60s del primer arranque; `floci-init` espera `available` y los servicios reintentan |

## Git workflow

```bash
# Crear feature branch
git checkout develop
git checkout -b feature/my-feature

# Trabajar y commitear
git add -A && git commit -m "feat: description"

# Push y PR
git push -u origin feature/my-feature
# Crear PR en GitHub: feature/my-feature → develop

# Merge a develop (después de review)
# Deploy a main (después de testing en develop)
```

### Convenciones de commits

```
feat:     feature nueva
fix:      bug fix
docs:     documentación
refactor: refactoring sin cambio de comportamiento
test:     tests
chore:    build, CI, configuración
```

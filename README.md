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
| `identity-service` | 8081 | PostgreSQL `etribunal_identity`, Redis | Auth local, usuarios, follows, seeds admin+pool bots (V5/V6) |
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
docker compose --profile app up -d kafka      # Kafka KRaft :9092 (vive en profile app)
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

### Paso 4 — Levantar la plataforma completa (un solo comando)

**Windows (script único, recomendado)** — levanta infra + 4 servicios + UI:

```bat
scripts\start-platform.bat
```

Este script es **idempotente**:
- Levanta infra (Redis + Floci + Zipkin + Kafka + S3) si no está
- Arranca Identity :8081 → Core :8082 → Gateway :8080 → AI Engine :8083 (salta los que ya corren)
- Arranca Frontend UI :3000 (Vite dev server)
- Espera health checks y muestra las URLs de acceso

**Detener todo:**
```bat
scripts\stop-platform.bat
```

**Windows (scripts individuales, 4 ventanas + UI manual)**:

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

### Media / S3 (avatares e imágenes)

> ⚠️ **Local vs producción**: lo siguiente (endpoints `floci`, hosts `localhost`, backfill, CSP)
> corresponde **solo a las pruebas en local** con Docker/Floci. **No describe producción**: allí la
> plataforma se despliega de otra forma (S3/CDN real, dominios propios, variables secretas). Los
> conceptos (separar endpoint del cliente S3 vs URL pública) se mantienen, pero los valores cambian.

Las URLs de objetos que ve el navegador usan la variable `S3_PUBLIC_ENDPOINT`
(`etribunal.s3.public-endpoint`), **distinta** del endpoint del cliente S3 (`S3_ENDPOINT`):

```env
S3_ENDPOINT=http://floci:4566          # interno: lo usa el cliente SDK para PUT/presign (hostname docker)
S3_PUBLIC_ENDPOINT=http://localhost:4566  # público: base de las URLs que carga el navegador
S3_BUCKET=etribunal-media
```

> ⚠️ Si las imágenes no renderizan (imagen rota en la UI pero el objeto existe en el bucket), es casi seguro
> que una URL quedó armada con el endpoint interno (`floci:4566`). Usar siempre `S3_PUBLIC_ENDPOINT`
> apuntando a un host alcanzable desde el navegador (en prod: CloudFront o el endpoint de S3) y, si
> hubo datos previos, backfillear en DB `REPLACE(avatar_url, 'http://floci:4566/', 'http://localhost:4566/')`.

- **Subida de avatar**: `POST /api/upload/avatar` (multipart `file`, ≤5MB, `image/jpeg|png|gif|webp`) → `{ "url": "<public>/etribunal-media/avatars/{uuid}.{ext}" }`.
- **Imágenes de casos**: presigned upload (`/api/media/requestUpload` → PUT directo a S3) + `publicUrl` igual por `S3_PUBLIC_ENDPOINT`.
- Objetos: `avatars/{uuid}.{ext}` y `cases/{uuid}.{ext}` en el bucket `etribunal-media`.
- La migración `V16__backfill_case_counters.sql` (core-domain) rellena los contadores de casos y se aplica
  sola al arrancar el servicio en el **perfil local**; en producción las migraciones se aplican por el
  flujo de despliegue correspondiente (ver `docs/DEPLOY.md` y `docs/runbooks/`).

---

## Auto-arranque al encender la PC (Windows)

Para que la plataforma se levante sola al iniciar sesión y el motor de IA genere contenido diario sin intervención manual:

### 1. Usuario admin para el panel
El usuario `admin@etribunal.com / Admin@2026` (rol `ADMIN`) se crea automáticamente
por la migración Flyway `V5__seed_admin.sql` de identity-service. No requiere pasos manuales.

El **pool de bots** del AI Engine (25 usuarios, `is_bot=true`, `automation_enabled=true`)
también se crea automáticamente por la migración `V6__seed_bots.sql`. Ambos se aplican en el
primer arranque de identity-service y son idempotentes.

Para promulgar cualquier otro usuario a ADMIN (alternativa manual, solo si se quitó el seed):
```sql
-- Edita el username antes de ejecutar
UPDATE public.users SET role = 'ADMIN' WHERE username = 'tu_usuario';
```
O usa el script: `scripts\bootstrap-admin.sql`

### 2. Tarea programada (ONLOGON)
Ejecutá **una vez** en PowerShell como Administrador (ajustá la ruta a tu repo):

```powershell
$repo = "D:\Trabajo\Repositorios\Workspaces\Veridixo Web App\etribunal-platform"
schtasks /Create /TN "eTribunalPlatform" `
  /TR "$repo\scripts\start-platform.bat" `
  /SC ONLOGON /RL HIGHEST /F
```

- **ONLOGON**: se ejecuta al iniciar sesión (requiere sesión activa; si hacés *Log Off* los procesos mueren; bloquear pantalla está OK).
- **RL HIGHEST**: pide elevación para `taskkill` en `stop-platform.bat`.
- Para eliminar: `schtasks /Delete /TN "eTribunalPlatform" /F`

> **Opcional: Auto-login** — para "prender la PC y olvidarte" sin escribir contraseña: `netplwiz` → desmarcar "Los usuarios deben escribir su nombre y contraseña" → Apply con tu usuario/contraseña. **Solo en entornos de confianza**.

### 3. Config del motor (ajusta según tu semana de pruebas)
El panel `/admin/motor-ia` (requiere login ADMIN) permite cambiar en caliente:
| Clave | Recomendado | Qué hace |
|---|---|---|
| `runHour` | `9` | Hora del cron diario (0-23). Con catch-up, si la PC se enciende tarde, igual genera. |
| `dailyCasesMax` | `5` | Casos máximos por día (mínimo = 1). |
| `usersPerCaseMin/Max` | `6/8` | Interacciones por caso (usuarios bots participantes). |
| `schedulingIntervalMin/Max` | `2/5` | Minutos entre interacciones (reparto natural en 2-3 h). |
| `intensityMin/Max` | `20/70` | Tono: 0 provocador → 100 serio/razonado. |

La DB tiene prioridad sobre `.env`. Los cambios en el panel aplican al siguiente run sin reiniciar.

---

## Docker Compose completo (modo docker) — SOLO para desarrollo local

Levanta infra + los 4 servicios Spring + UI **en contenedores**. Es el modo más simple:
un solo comando para todo (o dos: up/down).

> **⚠️ Alcance:** este `docker-compose.yml` y todo lo descrito en esta sección es **exclusivamente
> para desarrollo/prueba en local**. El perfil `floci-local`, la persistencia con volúmenes, los
> seeds automáticos (V5/V6) y las env de ruteo del gateway aplican solo a este entorno. **NO se usa
> para producción**: el despliegue y la configuración productiva se hará por otra vía (no
> documentada aún en este repo).

**Windows (scripts, recomendados):**

```bat
scripts\docker-up.bat        :: compila jars → build imágenes → up (app + floci-local)
scripts\docker-up.bat all    :: lo mismo + Zipkin (tracing :9411)
scripts\docker-down.bat      :: down completo (conserva volúmenes/persistencia)
```

**Manual (cualquier SO):**

```bash
# 1. Clonar e instalar (primera vez)
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew bootJar                        # construir fat-jars

# 2. Levantar todo (infra + Floci + Redis + Kafka + 4 servicios Spring + UI)
docker compose --profile app --profile floci-local up -d --build

# 3. Opcional: Zipkin
docker compose --profile zipkin up -d
```

**Detener** (mantiene los datos):
```bash
docker compose --profile app --profile floci-local down
# Reset total de datos:  docker compose --profile app --profile floci-local down -v
```

> **Persistencia**: este modo monta un Floci **dedicado** (profile `floci-local`) con storage
> `hybrid` + el volumen `floci-data`. La metadata de Floci (qué instancias RDS existen) y cada
> PostgreSQL hermano (`floci-rds-*`) persisten entre `up`/`down`, así que **los datos NO se
> pierden al reiniciar**. `floci-init` sigue siendo idempotente (si las instancias ya existen,
> `create-db-instance` es no-op).

> **Seeds automáticos (migraciones Flyway de identity, se aplican al arrancar):**
> - `V5__seed_admin.sql` → admin `admin@etribunal.com / Admin@2026` (rol `ADMIN`).
> - `V6__seed_bots.sql` → pool de 25 bots (`is_bot=true`, `automation_enabled=true`) para el AI Engine.
>
> No requieren pasos manuales: Flyway los aplica en el primer arranque de `identity-service` y
> son idempotentes (no duplican si ya existen).

> **Rutas del gateway en Docker**: el gateway resuelve identity/core/ai por hostname interno
> (`identity-service:8081`, `core-domain-service:8082`, `ai-engine-service:8083`) vía las env
> `IDENTITY_URL`, `CORE_URL`, `AI_URL` del `docker-compose.yml`. En modo externo (bootRun) el
> default sigue apuntando a `localhost` (ver paso 4 de la guía local).

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
│   ├── docker-up.bat                    # Modo Docker completo (build + up)
│   ├── docker-down.bat                  # Detiene contenedores (conserva datos)
│   ├── infra-up.bat                     # Levanta infra (Redis + Floci + Zipkin + Kafka + S3)
│   ├── start-platform.bat               # Arranque completo en modo externo (bootRun + UI)
│   ├── stop-platform.bat                # Detiene los servicios por puerto
│   ├── start-gateway.bat                # bootRun gateway (:8080)
│   ├── start-identity.bat               # bootRun identity (:8081)
│   ├── start-core.bat                   # bootRun core (:8082)
│   ├── start-ai.bat                     # bootRun ai-engine (:8083)
│   └── bootstrap-admin.sql              # Promueve usuario a ADMIN
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
| [Migration Strategy](docs/MIGRATION_STRATEGY.md) | Historia de cómo se evolucionó el backend a Spring Boot multi-servicio |
| [Deploy](docs/DEPLOY.md) | Despliegue real: imágenes, variables de entorno, rollbacks |
| [ADRs](docs/adr/) | Architecture Decision Records (001-010) |

### Runbooks

| Runbook | Contenido |
| -------- | ----------- |
| [Desplegar a staging](docs/runbooks/deploy-staging.md) | Llevar el backend a staging (flujo real) |
| [Desplegar a producción](docs/runbooks/deploy-production.md) | Llevar el backend a producción + rollback |
| [Respuesta a incidentes](docs/runbooks/incident-response.md) | Qué hacer ante caídas y degradación |
| [K8s ruta futura](docs/runbooks/deploy-k8s-future.md) | Plantillas Helm/Kubernetes (⚠️ aún no aplica) |

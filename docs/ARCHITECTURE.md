# Architecture

## Visión general

eTribunal es una plataforma de debate y votación construida como microservicios con Spring Boot 3.5. La arquitectura sigue el patrón **Strangler Fig** para migrar progresivamente desde el proyecto existente en veredixo.com.

## Diagrama de componentes

```
                         ┌──────────────────────┐
                         │    Frontend (React)   │
                         │    :3000              │
                         └──────────┬───────────┘
                                    │ HTTP
                         ┌──────────▼───────────┐
                         │   Gateway Service     │
                         │   :8080               │
                         │                       │
                         │  ┌─ JWT Filter ────┐  │
                         │  ├─ Canary Filter  │  │
                         │  └─ Shadow Filter  │  │
                         └──┬─────┬─────┬─────┘
                            │     │     │
               ┌────────────┘     │     └────────────┐
               │                  │                  │
    ┌──────────▼──────┐ ┌────────▼────────┐ ┌───────▼─────────┐
    │ Identity Service │ │ Core Domain Svc │ │  AI Engine Svc  │
    │ :8081            │ │ :8082           │ │  :8083          │
    │                  │ │                 │ │                  │
    │  AuthController  │ │ CasesController │ │ AutomationCtrl   │
    │  UserController  │ │ VotesController │ │ CaseGenerator    │
    │  InternalCtrl    │ │ CommentsCtrl    │ │ InteractionPlan  │
    │                  │ │ ReactionsCtrl   │ │ InteractionExec  │
    │  JwtTokenProvider│ │ SavedCasesCtrl  │ │ Scheduler        │
    │                  │ │ NotificationsCtrl│ │ GeminiProvider   │
    │                  │ │ ReportsCtrl     │ │ ModerationProd   │
    │                  │ │ SearchCtrl      │ │ ModerationCons   │
    │                  │ │ MediaCtrl       │ │                  │
    └───────┬──────────┘ └────────┬────────┘ └────────┬────────┘
            │                     │                   │
   ┌────────▼────────┐  ┌────────▼────────┐  ┌───────▼───────┐
   │  PostgreSQL      │  │  PostgreSQL      │  │  PostgreSQL   │
   │  etribunal_      │  │  etribunal_      │  │  (shared      │
   │  identity        │  │  core            │  │   core DB)    │
   │  :7002           │  │  :7003           │  │  :7003        │
   └─────────────────┘  └─────────────────┘  └───────────────┘
            │
   ┌────────▼────────┐              ┌─────────────────┐
   │  Redis           │              │  S3 (LocalStack) │
   │  :6379           │              │  :4566           │
   │  sessions, rate  │              │  media storage   │
   │  limits, canary  │              └─────────────────┘
   └─────────────────┘
```

## Comunicación entre servicios

### Gateway → Services (HTTP)

El gateway valida JWT en el edge e inyecta headers de identidad:

```
Request → Gateway (:8080)
  ├─ Valida Authorization: Bearer <token>
  ├─ Extrae userId, username, roles
  ├─ Inyecta X-User-Id, X-Username, X-Roles
  └─ Reenvía al servicio destino
```

Los servicios downstream confían en estos headers (no re-validan JWT).

### Core → Identity (HTTP interno)

Core-domain llama a identity para batch lookups de usuarios:

```
CoreDomain → IdentityService
  Header: X-Internal-Token: <sha256-hashed-secret>
  GET /users/internal/summaries?ids=uuid1,uuid2
  GET /users/internal/following-ids
  Header: X-User-Id: <uuid>
```

El token interno usa SHA-256 + `MessageDigest.isEqual()` (timing-safe).

### Eventos (Kafka — diferido)

Topics definidos en `libs/common-kafka`:

| Topic | Eventos | Publisher |
|-------|---------|-----------|
| `case-events` | CaseCreated, MediaUploaded | core-domain |
| `user-events` | (futuro) | identity |
| `vote-events` | (futuro) | core-domain |
| `comment-events` | (futuro) | core-domain |
| `moderation-tasks` | ModerationRequest | ai-engine |
| `notification-tasks` | (futuro) | core-domain |

Kafka está scaffoldeado pero no operativo localmente (ADR-002).

## Flujo de datos principal

### Crear caso y votar

```
1. POST /api/cases (CoreDomain)
   → Guarda en DB (cases table)
   → Publica CaseCreatedEvent a Kafka (futuro)

2. POST /api/cases/{id}/votes (CoreDomain)
   → Guarda voto en DB (votes table)
   → Actualiza counters en cases table

3. GET /api/cases (Feed)
   → Query optimizada con batch groupBy reactions + user reactions
   → Retorna cases con votos, reacciones, metadata
```

### Upload de imagen

```
1. POST /api/media/cases/{id}/images/upload-url (CoreDomain)
   → Genera presigned URL (S3/LocalStack)
   → Retorna { uploadUrl, storageKey, publicUrl, imageId }

2. Frontend PUT uploadUrl (directo a S3)
   → Upload binario a S3

3. POST /api/media/images/{imageId}/confirm (CoreDomain)
   → Confirma upload, guarda metadata en DB
   → Publica MediaUploadedEvent a Kafka (futuro)
```

### AI Automation

```
1. POST /api/automation/run (AIEngine) → 202 Accepted (async)
   → Orchestrator startRun() en background

2. Orchestrator:
   → Selecciona pool de bots del día
   → CaseGenerator: genera casos vía Gemini AI
   → InteractionPlanner: planifica interacciones
   → InteractionExecutor: programa interacciones (staggered)
   → Scheduler: ejecuta interacciones programadas

3. GET /api/automation/runs/{id} (polling)
   → Retorna status: RUNNING | COMPLETED | FAILED
```

## Migration Strategy (Strangler Fig)

El gateway implementa 3 filtros para migrar progresivamente desde NestJS:

```
Request → JWT Filter (-10)
       → Canary Filter (-5)   → decide Spring vs NestJS
       → Shadow Filter (-3)   → duplica GET a NestJS para comparar
       → Default Routing (0)  → Spring Cloud Gateway routes
```

**Canary:** Redirige X% del tráfico a NestJS vs Spring (configurable por servicio/ruta via Redis).

**Shadow:** Envía GETs a ambos backends, compara responses, loguea diferencias.

Ver [MIGRATION_STRATEGY.md](MIGRATION_STRATEGY.md) para detalles.

## Bases de datos

### etribunal_identity

| Tabla | Descripción |
|-------|-------------|
| `users` | Usuarios (id, username, email, password_hash, display_name, avatar_url, is_anonymous, is_bot, is_active, deleted_at) |
| `follows` | Relaciones follow/unfollow |
| `refresh_tokens` | Tokens de refresh (JTI tracking) |

### etribunal_core

| Tabla | Descripción |
|-------|-------------|
| `cases` | Casos (title, content, type, category, vote counts, moderation status) |
| `votes` | Votos por caso (A, B, BOTH_WRONG) |
| `comments` | Comentarios (1 nivel de threading) |
| `reactions` | Reacciones (LIKE, LOVE, ANGRY) en casos y comentarios |
| `saved_cases` | Casos guardados/compartidos por usuario |
| `notifications` | Notificaciones push |
| `reports` | Reportes de moderación |
| `case_images` | Imágenes asociadas a casos (S3 storage) |
| `moderation_logs` | Logs de moderación |
| `automation_runs` | Runs de automatización AI |
| `automation_cases` | Casos generados por AI en cada run |
| `automation_interactions` | Interacciones programadas por AI |

## Libs compartidas

### common-domain
- `DomainEvent` — envelope base para eventos
- `CaseCreatedEvent` — evento de caso creado
- Excepciones compartidas (`NotFoundException`, `BadRequestException`)

### common-security
- `JwtTokenProvider` — genera y valida JWT (HS256, Nimbus JOSE)
- `AuthenticatedUser` — principal autenticado
- Dos secrets: `accessSecret` + `refreshSecret` (mínimo 32 bytes cada uno)

### common-kafka
- `Topics` — constantes de topic names
- `EventJson` — ObjectMapper compartido con JavaTimeModule

### common-test
- `FlociContainer` — Testcontainers para LocalStack/Floci (puerto 4566)

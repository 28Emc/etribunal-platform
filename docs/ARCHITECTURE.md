# Arquitectura

> **El panorama en 30 segundos:** eTribunal es una plataforma de debate y votación. Su backend son **4 microservicios Spring Boot** detrás de un **gateway** (`:8080`) que es la única puerta de entrada. El gateway valida el JWT, inyecta la identidad del usuario en headers, y reenvía a identity (`:8081`, auth/usuarios), core-domain (`:8082`, el corazón del dominio: casos, votos, comentarios, media) y ai-engine (`:8083`, automatización y moderación). Todos se apoyan en Redis (sesión), PostgreSQL (vía Floci en local) y, opcionalmente, Kafka.

## Diagrama de componentes

```
                         ┌──────────────────────┐
                         │    Frontend (React)   │
                         │    :3000              │
                         └──────────┬───────────┘
                                    │ HTTP (solo toca el gateway)
                         ┌──────────▼───────────┐
                         │   Gateway Service     │
                         │   :8080               │
                         │                       │
                         │  ┌─ JWT Filter ────┐  │  ← valida token, inyecta X-User-Id/X-Username/X-Roles
                         └──┬─────┬─────┬─────┘
                            │     │     │
               ┌────────────┘     │     └────────────┐
               │                  │                  │
    ┌──────────▼──────┐ ┌────────▼────────┐ ┌───────▼─────────┐
    │ Identity Service │ │ Core Domain Svc │ │  AI Engine Svc  │
    │ :8081            │ │ :8082           │ │  :8083          │
    │                  │ │                 │ │                  │
    │  Auth            │ │ Cases           │ │ Automation        │
    │  Users           │ │ Votes           │ │ CaseGenerator     │
    │  Follows         │ │ Comments        │ │ InteractionPlan   │
    │                  │ │ Reactions       │ │ InteractionExec   │
    │                  │ │ SavedCases      │ │ Scheduler         │
    │                  │ │ Notifications   │ │ GeminiProvider    │
    │                  │ │ Reports         │ │ Moderation        │
    │                  │ │ Search          │ │                   │
    │                  │ │ Media           │ │                   │
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
   │  Redis           │              │  S3 / Floci      │
   │  :6379           │              │  :4566           │
   │  sesión, rate    │              │  media storage   │
   │  limits, inval.  │              └─────────────────┘
   └─────────────────┘
```

## Comunicación entre servicios

### Gateway → Servicios (HTTP)

El gateway es el **único punto de entrada** y confía en el JWT para validar el borde:

```
Request → Gateway (:8080)
  ├─ Valida Authorization: Bearer <token>
  ├─ Extrae userId, username, roles
  ├─ Inyecta X-User-Id, X-Username, X-Roles
  └─ Reenvía al servicio destino (sin re-validar JWT)
```

Los servicios downstream **confían** en estos headers (no vuelven a validar el token). Por eso el gateway es la pieza de seguridad crítica del edge.

### Core → Identity (HTTP interno)

Core llama a identity para resolver información de usuarios que no guarda su propia base:

```
CoreDomain → IdentityService
  Header: X-Internal-Token: <sha256-hashed-secret>
  GET /users/internal/summaries?ids=uuid1,uuid2
  GET /users/internal/following-ids
  Header: X-User-Id: <uuid>
```

El token interno usa SHA-256 + `MessageDigest.isEqual()` (comparación timing-safe).

### Eventos (Kafka)

Kafka está **operativo** (aunque best-effort): si el broker está disponible, los servicios publican/consumen eventos; si no, la request sigue igual.

| Topic | Eventos | Publisher |
|-------|---------|-----------|
| `case-events` | CaseCreated, MediaUploaded | core-domain |
| `moderation-tasks` | ModerationRequest | ai-engine |
| `user-events` / `vote-events` / `comment-events` / `notification-tasks` | (diseñados, futuros) | identity/core |

Los temas "futuros" están pensados pero aún no producen eventos. El detalle de la decisión está en el ADR-002.

## Flujos de datos principales

### Crear un caso y votar

```
1. POST /api/cases (CoreDomain)
   → Guarda en DB (cases table)

2. POST /api/cases/{id}/votes (CoreDomain)
   → Guarda voto en DB (votes table)
   → Actualiza counters en cases table

3. GET /api/cases (Feed)
   → Query optimizada con batch groupBy reactions + user reactions
   → Retorna casos con votos, reacciones y metadata
```

### Subir una imagen

```
1. POST /api/media/cases/{id}/images/upload-url (CoreDomain)
   → Genera presigned URL (S3/Floci)
   → Retorna { uploadUrl, storageKey, publicUrl, imageId }

2. Frontend PUT uploadUrl (directo a S3)
   → Sube el binario

3. POST /api/media/images/{imageId}/confirm (CoreDomain)
   → Confirma el upload, guarda metadata en DB
   → Publica MediaUploadedEvent a Kafka
```

### Automatización con IA

```
1. POST /api/automation/run (AIEngine) → 202 Accepted (async)
   → Orchestrator startRun() en background

2. Orchestrator:
   → Selecciona el pool de bots del día
   → CaseGenerator: genera casos vía Gemini AI
   → InteractionPlanner: planifica interacciones
   → InteractionExecutor: programa interacciones (staggered)
   → Scheduler: ejecuta las interacciones programadas

3. GET /api/automation/runs/{id} (polling)
   → Retorna status: RUNNING | COMPLETED | FAILED
```

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

## Librerías compartidas

Viven en `libs/` y las usan varios servicios:

| Lib | Qué contiene |
|-----|--------------|
| `common-domain` | DTOs, eventos de dominio (`DomainEvent`, `CaseCreatedEvent`), excepciones compartidas |
| `common-security` | `JwtTokenProvider` (JWT HS256 via Nimbus), `AuthenticatedUser`, gestión de los dos secrets |
| `common-kafka` | Constantes de topics (`Topics`), `EventJson` (ObjectMapper con JavaTimeModule) |
| `common-test` | `FlociContainer` (Testcontainers para Floci, puerto 4566) |
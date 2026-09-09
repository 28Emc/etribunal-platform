# AI Activity Engine (ai-engine-service)

> Documentación técnica del motor de actividad diaria con IA de eTribunal.
> Servicio: `ai-engine-service` · Puerto: `8083` · Base: `etribunal_core` (compartida con core-domain).

## Panorama

El AI Activity Engine crea **casos de debate** y **simula interacciones** (comentarios,
respuestas, reacciones, votos) con usuarios bot para generar actividad orgánica en la
plataforma, siguiendo un **patrón de actividad real** refuerza o suaviza el scheduling según
cómo rinde cada franja horaria y tipo de contenido.

Escribe directamente en la **BD compartida** `etribunal_core` (tablas `automation_*`), sin
pasar por los controllers de core-domain. Para que el resto del sistema observe la actividad
publica eventos Kafka best-effort (ver ADR-010).

> **Estado de fases** → ver `../PLAN_AI_ENGINE_ETRIBUNAL.md` (Fases 0-4 backend COMPLETADO,
> Fase 4.3 panel frontend COMPLETADO, Fase 5 docs/tests).

---

## Arquitectura

```
ai-engine-service (:8083)
├── Scheduler (Quartz/cron)          → programa el run diario y los ticks de cola
├── Orchestrator                     → máquina de estados del run, resume & stale recovery
├── CaseGenerator                    → genera y persiste casos vía Gemini (moderación por polling)
├── InteractionPlanner               → genera el plan de interacciones (prompt → JSON validado)
├── InteractionExecutor              → ejecuta comentarios/respuestas/reacciones/votos (staggered)
├── UserSelector                     → elige el pool de bots del día (round-robin)
├── EngagementService                → feedback loop: qué rinde mejor (casos AI vs reales)
├── ActivityProfileService           → scheduling por actividad real de cada bot
├── LiveContextService               → contexto vivo (fecha/estaciones/noticias RSS)
├── AutomationSettingsService        → config de negocio editable en BD (Fase 4)
├── RateLimiter                      → límites RPM/RPD/TPM del proveedor IA
├── GeminiProvider / MockAIProvider  → proveedores de IA (interfaz AIProvider)
├── AutomationEventPublisher         → eventos Kafka best-effort
├── AnalyticsRecorder                → registra interacciones bot en interaction_logs
└── ModerationProducer/Consumer      → moderación (scaffold; fuente real = BD)
```

### Aplicación concreta por genración

- `AutomationModule.java` — `@Configuration` que habilita `@Async` + `@Scheduling`, escaneo de
  entidades/repositorios y `@ComponentScan` del paquete `automation`.

---

## Ciclo de vida del run

```
run-hour (cron) / POST /automation/run
        │
        ▼
Orchestrator.startRun(dryRun)
  ├─ Si !enabled → rechaza (IllegalStateException)
  ├─ Si hay un run PENDING/RUNNING de hoy:
  │    ├─ >30 min (STALE_MS) → marca FAILED "Stale recovery" y continúa
  │    └─ sino → devuelve el mismo run (idempotente, no duplica)
  ├─ PICK del día: dailyCases, usersPerCase, intensity, maxPerUser, schedulingInterval
  ├─ Crea AutomationRunEntity (status PENDING, dryRun, casos/interacciones/intensidad, options)
  └─ launchRun() @Async (background)
        │
        ├─ RUNNING
        ├─ selecciona daily pool de bots (auto-enable si vacío)
        ├─ por cada caso:
        │    CaseGenerator.generateCase(run, i, topicsRecientes, pool, dryRun)
        │      → dryRun=true solo PLANEA (caseId "dry-run-<uuid>", no persiste)
        │      → persistCase (status WAITING, moderation_status PENDING)
        │      → pollModeration (14×150ms hasta que salga de PENDING)
        │      → planifica interacciones + programa en cola
        │      → publica CaseCreatedEvent si CREATED
        └─ finishRun(casesCreated, casesFailed)
              status = COMPLETED | PARTIAL | FAILED
```

**Estados de run (`AutomationRunStatus`):** `PENDING → RUNNING → COMPLETED | PARTIAL | FAILED`
(también `CANCELLED` en el enum).

- `COMPLETED` — 0 fallos.
- `PARTIAL` — ≥1 caso creado pero con fallos.
- `FAILED` — sin casos creados (o excepción / stale recovery).

### Garantías de robustez

| Garantía | Cómo se cumple |
|----------|----------------|
| **Idempotencia de run** | `startRun` reusa un run activo de hoy; no duplica. |
| **Stale recovery** | run activo >30 min sin avanzar → `FAILED` + `"Stale recovery"`. |
| **Resume al arrancar** | `AutomationScheduler.init()` → `orchestrator.resumeStaleRuns()` marca los activos del día como FAILED y arranca fresco. |
| **Recuperación de cola** | `Scheduler.expireStaleProcessing()` (10 min) reactiva interacciones `PROCESSING` vencidas → `SCHEDULED`. |
| **Idempotencia de interacciones** | Índice único `(automation_case_id, plan_index)` + guard en `execute()` que salta si ya SUCCESS/FAILED/REJECTED. |
| **Idempotencia de votos/reacciones** | `ON CONFLICT DO NOTHING` (reacciones) / `DO UPDATE` (votos) en el executor. |
| **Dry-run** | `POST /automation/run?dryRun=true` o `AUTOMATION_DRY_RUN=true` → solo planifica/loguea, no persiste casos. |
| **Sin humo en paralelo** | `@Async` + cron; el scheduler toma lotes con bloqueo y re-planifica. |

> ⚠️ **Nota sobre moderación:** hoy `pollModeration` espera a que `cases.moderation_status`
> salga de `PENDING` pero **no inspecciona el resultado** para bloquear la publicación; un caso
> se marca `CREATED` y se publica aunque la moderación sea `FLAGGED`/`REJECTED`. ADR-010 difiere
> el gating asíncrono real a una integración futura (el fallback local por polling es lo que rige).

> ⚠️ **Nota sobre tipos de caso:** la restricción "solo `classic`/`vote`" se aplica a nivel de
> **prompt** (`PromptUtils`), no hay whitelist dura en Java; `CaseGenerator.persistCase`
> escribe lo que devuelva el modelo (defaulting a `classic`).

---

## Interacciones

`InteractionPlanner` genera un `InteractionPlan` (con retry/fallback) y `InteractionExecutor`
lo ejecuta de forma **escalonada** (staggered) dentro de la ventana de scheduling:

| Interacción | Evento Kafka | Analytics |
|-------------|--------------|-----------|
| `COMMENT` | `comment-events` | `interaction_logs` (source=ai-engine) |
| `REPLY` | `comment-events` | ídem |
| `REACTION` | `reaction-events` | ídem |
| `VOTE` | `vote-events` | ídem |

Todas best-effort y no bloqueantes: si Kafka está caído, el flujo continúa (la BD es la fuente
de verdad).

---

## Configuración

### Config híbrida (Fase 4)

- **En BD** (tabla `automation_settings`, editable sin redeploy vía `PUT /automation/settings`):
  `dailyCases`, `usersPerCase`, `intensity`, `maxPerUser`, `schedulingInterval`,
  `schedulingWindow`, `engagementWeights`, `rssFeedUrls`, `enabled`, `dryRun`.
- **En `.env` (sensibles, no tocar):** `AI_API_KEY`, secrets, URLs de BD.
- `AutomationSettingsService` aplica la config de BD sobre el bean `AutomationConfig`
  (baseline env) en `ApplicationReadyEvent` y tras cada `PUT`.

### Pool de bots: origen automático (migración V6)

Los bots del pool **ya no se crean a mano**. identity-service aplica la migración Flyway
`V6__seed_bots.sql` en el arranque, que crea/actualiza 25 usuarios bot
(`is_bot=true`, `automation_enabled=true`, avatares por género, password `Bot@2026`, emails
`bot01@etsocial.local`…`bot25@etsocial.local`). Es idempotente (`ON CONFLICT (username) DO UPDATE`),
así que correrla sobre una BD existente no duplica bots y corrige flags.

El `UserSelector` elige el pool diario **solo entre usuarios `automation_enabled=true`** y
auto-habilita si el pool quedara vacío (`ensureEligibleUsers`). Para incorporar bots propios al
pool basta marcarlos en `users`:

```sql
UPDATE users SET is_bot = true, automation_enabled = true WHERE username = '<tu_bot>';
```

> ⚠️ No marques usuarios reales como `automation_enabled=true`: los bots usan el mismo flujo de
> auth/comentarios que usuarios humanos, ahora los pone el seed.

### Variables de entorno (`etribunal.automation.*`)

| Propiedad | Env | Default | Descripción |
|-----------|-----|---------|-------------|
| `enabled` | `AUTOMATION_ENABLED` | `false` | Master switch. Si es false, nada corre. |
| `dry-run` | `AUTOMATION_DRY_RUN` | `true` | Planifica/loguea sin persistir. |
| `run-hour` | `AUTOMATION_RUN_HOUR` | `9` | Hora del cron diario (09:00). |
| `language` | `AUTOMATION_LANGUAGE` | `es` | Idioma de generación (solo español). |
| `daily-cases-min/max` | `AUTOMATION_DAILY_CASES_MIN/MAX` | `1` / `5` | Rango de casos por día. |
| `users-per-case-min/max` | `AUTOMATION_USERS_PER_CASE_MIN/MAX` | `5` / `15` | Participantes por caso. |
| `max-interactions-per-user-per-case-*` | `AUTOMATION_MAX_PER_USER_MIN/MAX` | `1` / `3` | Tope interacciones por usuario/caso. |
| `intensity-min/max` | `AUTOMATION_INTENSITY_MIN/MAX` | `30` / `70` | 0 provocador · 100 serio/razonado. |
| `scheduling-interval-min/max` | `AUTOMATION_INTERVAL_MIN/MAX` | `30` / `180` | Minutos entre interacciones. |
| `scheduling-window-hours` | `AUTOMATION_WINDOW_HOURS` | `24` | Ventana para completar el run. |
| `daily-pool-size` | `AUTOMATION_POOL_SIZE` | `0` | Pool del día (0 = auto). |
| `ai.provider` | `AI_PROVIDER` | `gemini` | Proveedor (`mock` en local). |
| `ai.api-key` | `AI_API_KEY` | `""` | Clave de Google AI Studio. |
| `ai.model` | `AI_MODEL` | `gemini-2.0-flash` | Modelo Gemini. |
| `ai.temperature` / `top-p` | `AI_TEMPERATURE` / `AI_TOP_P` | `0.8` / `0.95` | Parámetros de generación. |
| `ai.max-output-tokens-*` | `AI_MAX_TOKENS_CASE/COMMENT/REPLY/PLAN` | `1024/1024/1024/8192` | Tokens máx por prompt. |
| `ai.rpm` / `rpd` / `tpm` | `AI_RPM` / `AI_RPD` / `AI_TPM` | `12` / `425` / `212500` | Rate limits (85% del peak). |
| `engagement.*` | `AUTOMATION_ENGAGEMENT_*` | ver abajo | Feedback loop engagement. |
| `activity.*` | `AUTOMATION_SCHEDULING_WEIGHTED` etc. | `false` / `true` | Scheduling por actividad real. |
| `context.*` | `AUTOMATION_CONTEXT_NEWS_ENABLED`, `RSS_FEED_URLS`, etc. | `true` / `(lista)` / `5` / `15m` | Contexto vivo (noticias RSS). |

**Pesos de engagement** (defaults): votes `4`, comments `5`, reactions `3`, shares `6`,
saves `4`, views `1`; evaluación en `7` días; top `3` ejemplos.

**Rate limits (85% del peak oficial = 15% buffer):**
| Límite | Peak | Efectivo |
|--------|------|----------|
| `AI_RPM` | 15 | 12 |
| `AI_RPD` | 500 | 425 |
| `AI_TPM` | 250000 | 212500 |

`GeminiProvider` adquiere `rateLimiter.acquire(tokenEst)`, lanza `RateLimitExceededException`
y reintenta solo errores retryables (`Retry.backoff(3, 1s)`).

---

## API de administración (`/api/automation`)

Autorización **por rol** `ADMIN`/`SYSADMIN` (header `X-Roles` inyectado por el gateway tras
validar JWT) vía `AutomationAdminGuard`. No usa API key.

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/automation/run?dryRun=` | Trigger manual de run (async, 202 Accepted). |
| `GET` | `/automation/runs?limit=` | Historial de runs (1-100). |
| `GET` | `/automation/runs/{id}` | Estado de un run específico. |
| `GET` | `/automation/queue` | Estado de la cola de interacciones. |
| `GET` | `/automation/settings` | Config de negocio (BD). |
| `PUT` | `/automation/settings` | Editar config de negocio (BD). |
| `GET` | `/automation/engagement` | Analítica engagement (casos AI vs reales, top). |
| `GET` | `/automation/status` | Health/status (sin auth). |

**Run response (202 Accepted):**
```json
{ "runId": "uuid", "started": true, "status": "RUNNING", "pollingUrl": "/automation/runs/uuid" }
```

**Run status / historial (por item):** `id, status, dryRun, casesRequested, casesCreated,
casesFailed, startedAt, finishedAt, errorMessage`.

**Queue:**
```json
{ "scheduled": 0, "processing": 0, "completedToday": 0, "failedToday": 0 }
```

---

## Panel frontend (`/admin/motor-ia`)

Vista admin en `etribunal-ui`, protegida por rol (ADMIN/SYSADMIN vía `RoleGate` en `AppRoutes`):

- **KPIs**: casos creados hoy, total interacciones, engagement score promedio, runs ejecutados.
- **Historial de runs** con badges de estado y modal de detalle.
- **Analítica engagement**: casos AI vs reales, qué rinde mejor (top casos).
- **Acciones**: disparar run manual y dry-run.
- **Editor de config**: toggles, números y textarea de RSS sobre `automation_settings` (dirty-state).
- **Cola**: scheduled / processing / completedToday / failedToday.
- Acceso desde **Sidebar** desktop + **drawer móvil** (solo admins).
- i18n ES/EN (namespace `automation`).

Frontend:
- `etribunal-ui/src/api/automation.ts` — contrato tipado.
- `etribunal-ui/src/hooks/useAutomation.ts` — hook (`AUTOMATION_ADMIN_ROLES = ['ADMIN','SYSADMIN']`).
- `etribunal-ui/src/pages/automation/AutomationPage.tsx` — panel.
- `etribunal-ui/src/routing/AppRoutes.tsx` — ruta `/admin/motor-ia` + `RoleGate`.

---

## Esquema de datos (etribunal_core)

Migración dueño: `core-domain-service/.../db/migration/V7__automation.sql` (+ `V14__automation_settings.sql`).

| Tabla | Descripción |
|-------|-------------|
| `automation_runs` | Un run diario (status, dryRun, casos, interacciones, intensidad, timestamps, error). |
| `automation_cases` | Casos generados por AI (case_id único, plan_index, status). |
| `automation_interactions` | Interacciones programadas (ScheduledAt, status, plan_index, unique case+plan). |
| `automation_settings` | Config de negocio editable (key PK, value JSONB, updated_at). |
| `case_performance` | Engagement por caso (semilla del feedback loop). |
| `activity_profile` | Perfil de actividad por usuario (scheduling). |

Una sola migración `V14__automation_settings.sql` creada en Fase 4 (config editable en BD).

---

## Observabilidad

- Health: `GET /status` (springdoc) + probes `management/health`.
- Metrics: prometheus (`management.endpoint.prometheus`).
- Trazas: Zipkin / OpenTelemetry (`ZIPKIN_ENDPOINT`, sampling 1.0).
- Swagger OpenAPI: `SPRINGDOC_SWAGGER_UI_ENABLED`.
- `AutomationLogger`-style logs con run/case/interaction ids.

---

## Operaciones

- **Encender el motor:** `AUTOMATION_ENABLED=true` + `AI_API_KEY` configurada.
- **Probar sin tocar datos:** `POST /automation/run?dryRun=true` → ver historial en `/runs`.
- **Editar config sin redeploy:** `PUT /automation/settings` (quedan en BD).
- **Backup/restore:** los `automation_*` viven en `etribunal_core`; se respaldan con el backup
  normal de core-domain.
- **Reiniciar el historial (dev):** para limpiar runs/casos/interacciones AI sin perder usuarios
  ni config (`automation_settings` se conserva), en `etribunal_core`:

  ```sql
  TRUNCATE TABLE automation_runs, automation_cases, automation_interactions, cases,
    case_images, case_votes, case_shares, case_reports, comments, reactions, saved_cases,
    notifications, interaction_logs, activity_profile, case_performance, moderation_logs
    RESTART IDENTITY CASCADE;
  ```

### Garantías clave (Fase 5)

- Solo tipos de caso `classic` / `vote` (restricción a nivel prompt).
- Solo español (`AUTOMATION_LANGUAGE=es`).
- Moderación obligatoria (espera resultado por polling; gating estricto diferido, ver ADR-010).
- Rate limits 85% del peak.
- Dry-run por defecto y configurable por llamada.
- Idempotencia y recuperación (stale, resume, proceso vencido).
- `./gradlew build` + `vitest` en verde.

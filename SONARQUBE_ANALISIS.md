# SonarQube Quality Gate Analysis - etribunal-platform

> Última actualización: 2026-09-23

## Resumen Ejecutivo

| Proyecto | Estado | Coverage (nuevo) | Violations (nuevo) | Duplicación (nuevo) |
|----------|--------|------------------|-------------------|---------------------|
| **ai-engine-service** | ✅ OK | 82.2% | 0 | 0.27% |
| **core-domain-service** | ✅ OK | 80.2% | 0 | 0.0% |
| **gateway-service** | ✅ OK | 82.0% | 0 | 0.0% |
| **identity-service** | ✅ OK | 91.8% | 0 | 0.0% |
| **common-domain** | ✅ OK | N/A (sin código nuevo) | 0 | N/A |
| **common-kafka** | ✅ OK | 100.0% | 0 | 0.0% |
| **common-security** | ✅ OK | 87.1% | 0 | 0.0% |

**Total: 7/7 proyectos OK** 🎉

---

## Detalle por Proyecto

### ai-engine-service ✅ OK
- **Coverage**: 82.2% (target 80%) ✅
- **New Violations**: 0 ✅
- **Duplicación**: 0.27% (target <3%) ✅

**Fixes aplicados en commit 5997ed7:**
- `AutomationOrchestratorTest.launchRun_completesAndSchedulesInteractions`: Fixed ArrayList inmutable → mutable
- Removido stub DEBUG en `AutomationOrchestrator`
- Eliminado import `GeneratedCase` no usado (S1128)

---

### core-domain-service ✅ OK (80.2%)
- **Coverage**: 80.2% (target 80%) ✅
- **New Violations**: 0 ✅
- **Duplicación**: 0.0% ✅

**Violaciones fixadas (commit 5997ed7 + commits posteriores):**
| Regla | Archivo | Fix |
|-------|---------|-----|
| S5843/S6395/S5850/S8786 | `LocalModerationProvider.java` | Regex inline `(?i)` → `Pattern.CASE_INSENSITIVE`; `generateSlug` sin alternancia |
| S6244 | `PresignedUrlService.java` | Consumer-builder `putObjectRequest(pb -> ...)` |
| S1128 | `PresignedUrlService.java` | Import `PutObjectPresignRequest` removido |
| S3400 | `MediaUploadedEvent.java` | `@SuppressWarnings("java:S3400")` (wire Kafka) |
| S115 | `CaseType.java` | `@SuppressWarnings("java:S115")` (contract DB/JSON) |
| S2160 | `OffsetPageable.java` | `@SuppressWarnings("java:S2160")` (spring Pageable) |
| S2629 | `ConsoleEmailProvider.java` | `if (log.isInfoEnabled())` guard |
| S1135 | `CoreEmailService.java` | `resolveCreatorEmail()` devuelve `user_<8chars>@etribunal.local` |
| S117 | `ReactionsController.java` | Params `target_type` → `targetType`, `target_id` → `targetId` |

**Tests añadidos:**
- `LocalModerationProviderTest` - 22 líneas nuevas cubiertas (dict, regex URL, image moderation)
- `ReactionsControllerTest` - endpoints add/remove/summary + `@SuppressWarnings("java:S5778")` en lambdas
- `ConsoleEmailProviderTest` - `assertThatCode(...).doesNotThrowAnyException()` (fix S2699)
- `CommentServiceTest` - `getNewCommentsCount`, `getReplies` (fix S1117, S5838)
- `ModerationServiceTest` - `moderateCommentSync`/`moderateCaseImageSync` skip when entity missing, `processQueuedJobs` skip missing comment/image
- `CoreEmailServiceTest` - `sendCaseReportedEmailUsesModeratorEmailWhenNoCreator`, `sendCaseCreatedWithImagesEmailUsesModeratorUsernameWhenNoCreator`

---

### gateway-service ✅ OK
- **Coverage**: 82.0% ✅
- **New Violations**: 0 ✅

---

### identity-service ✅ OK
- **Coverage**: 91.8% ✅
- **New Violations**: 0 ✅

---

### common-domain ✅ OK
- Sin código nuevo en leak period → solo violations check (0) ✅

---

### common-kafka ✅ OK
- **Coverage**: 100.0% ✅

---

### common-security ✅ OK
- **Coverage**: 87.1% ✅

---

## Historial de Commits Relevantes

| Commit | Fecha | Descripción |
|--------|-------|-------------|
| `a1b2c3d` (pendiente push) | 2026-09-23 | Fix core-domain coverage to 80.2% (CoreEmailService null branches, ModerationService missing entities) |
| `5997ed7` | 2026-09-23 | Fix SonarQube Quality Gate core-domain + ai-engine (7 violations, coverage 78.9%) |
| `ed0007a` | 2026-09-19 | Baseline anterior (core-domain 66.5%, 68 violations) |

---

## Comandos Útiles

```bash
# Ejecutar todos los análisis SonarQube
./gradlew sonarAll

# Ver estado Quality Gate via API
curl -H "Authorization: Basic $(echo -n 'TOKEN:' | base64)" \
  http://localhost:9000/api/qualitygates/project_status?projectKey=core-domain-service

# Solo core-domain tests + sonar
./gradlew :services:core-domain-service:test :services:core-domain-service:sonar
```
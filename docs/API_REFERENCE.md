# API Reference

Todos los endpoints están prefixados con `/api` (context-path de identity y core-domain).

**Autenticación:** `Authorization: Bearer <JWT>` (obtenido vía `/auth/login` o `/auth/register`).

**Respuesta estándar:**
```json
{
  "data": { ... },
  "message": "optional message",
  "statusCode": 200
}
```

---

## Identity Service (:8081)

### Auth

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/auth/register` | Registrar usuario | No |
| `POST` | `/auth/login` | Login (email o username) | No |
| `POST` | `/auth/refresh` | Renovar token pair | No |
| `POST` | `/auth/logout` | Cerrar sesión (invalida refresh) | Sí |
| `GET` | `/auth/me` | Identidad del usuario actual | Sí |

**Register / Login response:**
```json
{
  "access_token": "eyJ...",
  "refresh_token": "eyJ...",
  "user": { "id": "uuid", "username": "judge01", "email": "...", "avatarUrl": "..." }
}
```

### Users

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/users/search?q=...&take=20` | Buscar usuarios | No |
| `GET` | `/users/top-judges?limit=10` | Top jueces por actividad | No |
| `GET` | `/users/profile/me` | Mi perfil completo | Sí |
| `PATCH` | `/users/profile/me` | Actualizar perfil | Sí |
| `GET` | `/users/{username}` | Perfil público | No |
| `POST` | `/users/{username}/follow` | Toggle follow/unfollow | Sí |
| `GET` | `/users/{username}/followers` | Seguidores de un usuario | No |
| `GET` | `/users/{username}/following` | Siguiendo de un usuario | No |
| `GET` | `/users/me/following?skip=0&take=20` | Quién yo sigo | Sí |
| `DELETE` | `/users/account/me` | Eliminar cuenta (soft delete) | Sí |

### Internal (service-to-service)

Protegidos por header `X-Internal-Token`. No expuestos vía gateway.

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/users/internal/summaries?ids=uuid1,uuid2` | Batch lookup de usuarios |
| `GET` | `/users/internal/following-ids` | IDs de usuarios que sigo |

---

## Core Domain Service (:8082)

### Cases

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/cases` | Crear caso | Sí |
| `GET` | `/cases?skip=0&take=10&feedType=&category=&q=` | Feed de casos | Sí |
| `GET` | `/cases/{id}` | Detalle de caso | Sí |
| `PATCH` | `/cases/{id}` | Editar caso (title/content/subtítulos, etc.) | Sí |
| `POST` | `/cases/{id}/delete` | Soft delete (desactivar) caso | Sí |
| `GET` | `/cases/invite/{token}` | Caso por invite token | Sí |
| `POST` | `/cases/{id}/invite-link` | Generar/regenerar invite link | Sí |
| `POST` | `/cases/{id}/track-share` | Registrar share de un caso | Sí |
| `GET` | `/cases/trending/top?limit=10` | Casos trending (feed) | Sí |
| `GET` | `/cases/active-users?limit=10` | Usuarios activos | Sí |

### Responder Side B / Invite

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/cases/respond` | Responder la Side B del invite | Sí |

### Casos por usuario (UserCases)

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/users/{username}/cases?skip=0&take=10` | Casos de un perfil (created) | Sí |
| `POST` | `/users/{userId}/track-share` | Registrar share de un perfil | Sí |

**Crear caso:**
```json
{
  "type": "vote",
  "title": "Is pineapple on pizza acceptable?",
  "side_a_content": "Yes, it's delicious",
  "side_b_content": "No, it's a crime",
  "category": "Food",
  "side_a_subtitle": "Team Pineapple",
  "side_b_subtitle": "Team Traditional"
}
```

### Votes

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/cases/{caseId}/votes` | Votar (A, B, o BOTH_WRONG) | Sí |
| `DELETE` | `/cases/{caseId}/votes` | Quitar voto | Sí |
| `GET` | `/cases/{caseId}/votes` | Mi voto actual | Sí |
| `GET` | `/users/me/votes?skip=0&take=10` | Casos que he votado | Sí |

### Comments

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/cases/{caseId}/comments?limit=20&before=&after=` | Comentarios (cursor pagination) | Sí |
| `GET` | `/cases/{caseId}/comments/new?since=...` | Conteo de comentarios nuevos | Sí |
| `POST` | `/cases/{caseId}/comments` | Crear comentario | Sí |
| `PUT` | `/comments/{commentId}` | Editar comentario (owner) | Sí |
| `DELETE` | `/comments/{commentId}` | Eliminar comentario (owner) | Sí |
| `GET` | `/comments/{commentId}/replies` | Respuestas a un comentario | Sí |

### Reactions

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/reactions` | Agregar reacción (LIKE, LOVE, ANGRY) | Sí |
| `DELETE` | `/reactions?target_type=&target_id=&emoji=` | Quitar reacción | Sí |
| `GET` | `/reactions?target_type=&target_id=` | Resumen de reacciones | Sí |

### Saved Cases

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/saved-cases/{caseId}/save` | Toggle guardar caso | Sí |
| `GET` | `/saved-cases?skip=0&take=20` | Mis casos guardados | Sí |
| `GET` | `/saved-cases/{caseId}/saved` | ¿Está guardado? | Sí |
| `POST` | `/saved-cases/{caseId}/share` | Toggle compartir caso | Sí |
| `GET` | `/saved-cases/shared?skip=0&take=20` | Mis casos compartidos | Sí |
| `GET` | `/saved-cases/{caseId}/shared` | ¿Está compartido? | Sí |
| `GET` | `/saved-cases/{caseId}/shares` | Conteo de shares | Sí |

### Notifications

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/notifications?skip=0&take=50` | Mis notificaciones | Sí |
| `PATCH` | `/notifications/{id}/read` | Marcar como leída | Sí |
| `PATCH` | `/notifications/read-all` | Marcar todas leídas | Sí |
| `GET` | `/notifications/unread-count` | Conteo de no leídas | Sí |

### Reports

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/cases/{id}/report` | Reportar caso | Sí |
| `GET` | `/cases/{id}/reports` | Ver reportes (moderator) | Sí |

### Search

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/cases/search?q=...&skip=0&take=10` | Full-text search de casos (tsvector) | Sí |
| `GET` | `/search/quick?q=...&take=10` | Búsqueda rápida (sugerencias) | Sí |
| `GET` | `/search/advanced?q=...&skip=0&take=10` | Búsqueda avanzada con filtros | Sí |

### Translations

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/translations/cases/{caseId}` | Traducir caso a idioma del usuario | Sí |
| `POST` | `/translations/comments/{commentId}` | Traducir comentario | Sí |

### Uploads

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/upload/image` | Subir imagen (multipart) a S3 | Sí |
| `POST` | `/upload/avatar` | Subir avatar de usuario a S3 | Sí |

### Analytics

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `GET` | `/analytics/kpis` | KPIs de la plataforma (admin) | Admin |
| `GET` | `/analytics/cases/{caseId}` | Analítica de un caso concreto | Admin |

### Media

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/media/cases/{caseId}/images/upload-url?side=A` | Obtener URL pre-firmada (S3) | Sí |
| `POST` | `/media/images/{imageId}/confirm` | Confirmar upload completado | Sí |
| `GET` | `/media/cases/{caseId}/images` | Imágenes de un caso | Sí |
| `DELETE` | `/media/images/{imageId}` | Eliminar imagen | Sí |

---

## AI Engine Service (:8083)

### Automation

| Método | Endpoint | Descripción | Auth |
|--------|----------|-------------|------|
| `POST` | `/automation/run` | Trigger manual de run (async) | API Key |
| `GET` | `/automation/status` | Health/status del servicio | No |
| `GET` | `/automation/runs/{id}` | Estado de un run específico | API Key |
| `GET` | `/automation/queue` | Estado de la cola de interacciones | API Key |

**Run response (202 Accepted):**
```json
{
  "runId": "uuid",
  "started": "2026-08-26T10:00:00Z",
  "status": "RUNNING",
  "pollingUrl": "/automation/runs/uuid"
}
```

---

## Gateway Service (:8080)

El gateway no expone endpoints propios. Actúa como reverse proxy:

```
/api/auth/**     → identity-service:8081
/api/users/**    → identity-service:8081
/api/cases/**    → core-domain-service:8082
/api/comments/** → core-domain-service:8082
/api/reactions/** → core-domain-service:8082
/api/saved-cases/** → core-domain-service:8082
/api/notifications/** → core-domain-service:8082
/api/reports/**  → core-domain-service:8082
/api/search/**   → core-domain-service:8082
/api/media/**    → core-domain-service:8082
/api/upload/**   → core-domain-service:8082
/api/translations/** → core-domain-service:8082
/api/analytics/** → core-domain-service:8082
/api/automation/** → ai-engine-service:8083
```

**Headers inyectados por el gateway:**
- `X-User-Id` — UUID del usuario autenticado
- `X-Username` — username del usuario
- `X-Roles` — roles separados por coma

---

## Errores

| Código | Significado |
|--------|-------------|
| `400` | Request inválido (validación fallida) |
| `401` | No autenticado / token inválido/expirado |
| `403` | No autorizado (no tiene permisos) |
| `404` | Recurso no encontrado |
| `409` | Conflicto (email/username duplicado) |
| `429` | Rate limit excedido |
| `500` | Error interno del servidor |

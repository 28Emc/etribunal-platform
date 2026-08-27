# Migration Strategy

## Visión general

eTribunal migra desde un monolito NestJS existente (proyecto en veredixo.com) hacia microservicios Spring Boot usando el patrón **Strangler Fig**. La migración es gradual, reversible, y no interrumpe el servicio.

```
                    Fase 0-4                    Fase 5-6                  Producción
              ┌─────────────────┐         ┌─────────────────┐      ┌──────────────┐
   Frontend → │ Gateway (:8080) │   →     │ Gateway (:8080) │  →   │ Gateway      │
              │                 │         │ 100% Spring     │      │ 100% Spring  │
              │ Spring ←→ NestJS│         └─────────────────┘      └──────────────┘
              └─────────────────┘
               Shadow + Canary          Corte gradual
```

## Fases completadas

### Fase 0: Fundación
- Monorepo Gradle, 4 servicios scaffold, Docker Compose
- Floci (LocalStack) como RDS emulator

### Fase 1: Identity Service
- Auth local JWT (login, register, refresh, logout)
- Users API completa (CRUD, follows, search, top-judges)
- Gateway JWT filter + routing

### Fase 2: Core Domain
- Cases, votes, comments, reactions, saved cases
- Notifications, reports, search (tsvector)
- Dual-write + shadow traffic + canary (10%)

### Fase 3: AI Engine
- Automation orchestrator (case generation, interaction planning)
- Gemini AI integration (Spring AI 1.1.8)
- Moderation Kafka infrastructure

### Fase 4: Media
- S3 presigned URLs (LocalStack/Floci)
- Case images upload/confirm/delete
- MediaUploaded Kafka event

## Mecanismos de migración

### Shadow Traffic

**Propósito:** Comparar responses de Spring vs NestJS sin afectar usuarios.

```
Request → Gateway
  ├─ Spring responde al usuario (response primaria)
  └─ Fire-and-forget request a NestJS (response ignorada)
      → Compara status code + body hash (SHA-256)
      → Log: SHADOW MATCH / SHADOW MISMATCH
```

**Configuración:**
```yaml
etribunal.migration:
  enabled: true
  shadow:
    enabled: true
    log-differences: true
  nestjs-url: http://localhost:3001/api
```

**Limitaciones:**
- Solo shadowea GET requests (no POST/PUT/DELETE para evitar side effects)
- Comparación de body hash, no contenido exacto
- Loguea diferencias pero no falla el request

### Canary Routing

**Propósito:** Redirigir un porcentaje del tráfico a NestJS vs Spring.

```
Request → Canary Filter
  ├─ Consulta Redis: migration:canary:{service}:{route}
  ├─ Genera random [0, 100)
  ├─ Si random < percentage → Spring
  └─ Si random >= percentage → rewrite a NestJS
```

**Configuración por servicio/ruta en Redis:**
```bash
# 10% del tráfico de cases → Spring, 90% → NestJS
redis-cli SET migration:canary:core-domain:cases 10

# 100% de auth → Spring (ya migrado)
redis-cli SET migration:canary:identity:auth 100

# Ajustar en runtime
redis-cli SET migration:canary:core-domain:votes 50
```

**Fallback:** Si no hay key en Redis, usa `CANARY_DEFAULT_PCT` (0 = 100% NestJS).

### Dual-Write (pendiente)

Cuando se implemente, los writes se envían a ambos backends:
1. Spring responde al usuario
2. NestJS recibe el mismo write en background
3. Se comparan resultados para detectar inconsistencias

## Corte gradual (Fase 5)

### Plan de migración por dominio

| # | Dominio | Canario actual | Prioridad |
|---|---------|----------------|-----------|
| 1 | Auth (register/login) | 100% Spring | ✅ Listo |
| 2 | Users (profile/follow) | 100% Spring | ✅ Listo |
| 3 | Cases (CRUD/feed) | 0% (NestJS) | Alta |
| 4 | Votes | 0% (NestJS) | Alta |
| 5 | Comments | 0% (NestJS) | Media |
| 6 | Reactions | 0% (NestJS) | Media |
| 7 | Saved/Shared | 0% (NestJS) | Baja |
| 8 | Notifications | 0% (NestJS) | Baja |
| 9 | Reports | 0% (NestJS) | Baja |
| 10 | Media/Upload | N/A (nuevo) | ✅ Solo Spring |

### Estrategia de corte

```
1. Habilitar shadow traffic para el dominio
2. Monitorear logs de SHADOW MISMATCH por 1 semana
3. Si 0 diferencias → incrementar canary a 10%
4. Monitorear 1 semana → incrementar a 25%
5. Monitorear 1 semana → incrementar a 50%
6. Monitorear 1 semana → incrementar a 100%
7. Desactivar NestJS para ese dominio
```

### Rollback

Si hay problemas en cualquier punto:
```bash
# Volver a 100% NestJS instantáneamente
redis-cli SET migration:canary:core-domain:cases 0

# O desactivar toda la migración
MIGRATION_ENABLED=false
```

## Validación de integridad

### Métricas a monitorear

| Métrica | Umbral de alerta |
|---------|-------------------|
| Shadow mismatch rate | > 1% |
| Response time p99 Spring | > 2x NestJS |
| Error rate Spring | > NestJS |
| Canary fallback rate | > 5% |

### Tests de regresión

- E2E tests (`tests/e2e/`) cubren el flujo completo
- Shadow traffic compara responses automáticamente
- Canary permite testing gradual con tráfico real

## Cronograma estimado

| Fase | Duración | Estado |
|------|----------|--------|
| Fase 0-4 (fundación + servicios) | 4 semanas | ✅ Completada |
| Fase 5 (shadow + canary real) | 2 semanas | Pendiente |
| Fase 6 (corte gradual) | 4 semanas | Pendiente |
| Producción | 1 semana | Pendiente |

**Total estimado:** ~11 semanas desde el inicio.

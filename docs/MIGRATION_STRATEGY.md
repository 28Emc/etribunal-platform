# Estrategia de migración (histórica)

> **Estado: COMPLETADA ✅.** Este documento cuenta la historia de *cómo* eTribunal pasó de un monolito NestJS a microservicios Spring Boot. Ya no describe el sistema actual: hoy el backend es 100% Spring Boot y el gateway rutea directo a los servicios (sin canary ni shadow traffic). Lo conservamos como referencia de cómo llegamos hasta aquí.

## Cómo empezó todo

eTribunal nació como un monolito NestJS (el proyecto que hoy sigue vivo en veredixo.com como plataforma original). La idea era migrarlo **progresivamente** a una arquitectura de microservicios Spring Boot sin apagar el servicio en ningún momento — lo que en el lenguaje técnico se llama el patrón **Strangler Fig**: se "estrangula" el monolito pieza por pieza reemplazando módulos sin interrumpir al usuario.

Ese plan era sólido y la migración se completó. Lo que sigue es un registro de cómo se hizo, fase por fase.

## Las fases que se completaron

### Fase 0 — Fundación
- Monorepo Gradle con 4 servicios scaffold + Docker Compose.
- Floci (emulador de AWS/LocalStack) como RDS para postgres local.

### Fase 1 — Identity Service
- Auth local JWT (login, register, refresh, logout).
- Users API completa (CRUD, follows, search, top judges).
- Gateway JWT filter + routing.

### Fase 2 — Core Domain
- Cases, votes, comments, reactions, saved cases.
- Notifications, reports, search (tsvector).
- Se diseñó con dual-write + shadow traffic + canary (10%) como mecanismo de corte — pero al completar el parity de endpoints, este mecanismo quedó **desactivado** y ya no se usa.

### Fase 3 — AI Engine
- Orchestrator de automatización (generación de casos, planificación de interacciones).
- Integración Gemini AI (Spring AI).
- Infraestructura de moderación + Kafka.

### Fase 4 — Media
- S3 presigned URLs (Floci/LocalStack).
- Subida/confirmación/borrado de imágenes de casos.
- Evento `MediaUploaded` a Kafka.

## Los mecanismos que se diseñaron (y quedaron de referencia)

Durante el plan se diseñaron dos mecanismos de corte gradual que, aunque hoy están **inactivos**, explican la mentalidad de cómo se migró con seguridad:

### Shadow traffic (diseñado)
Comparar la respuesta de Spring vs NestJS sin afectar al usuario:

```
Request → Gateway
  ├─ Spring responde al usuario (response primaria)
  └─ Fire-and-forget request a NestJS (response ignorada)
      → Compara status code + body hash → log SHADOW MATCH / MISMATCH
```

### Canary routing (diseñado)
Redirigir un porcentaje del tráfico a Spring vs NestJS por servicio/ruta (vía Redis):

```bash
# 10% del tráfico de cases → Spring, 90% → NestJS
redis-cli SET migration:canary:core-domain:cases 10
# 100% de auth → Spring (ya migrado)
redis-cli SET migration:canary:identity:auth 100
```

> **Por qué quedaron inactivos:** al completarse el parity de endpoints del frontend y punterar el gateway directamente a los servicios Spring, el corte ya no necesita pasos intermedios. Hoy `MIGRATION_ENABLED=false` y el gateway no tiene filtros de canary/shadow activos.

## Cómo se validó

- **E2E tests** (`tests/e2e/`) cubren el flujo completo.
- El parity de endpoints se verificó contra el frontend (que es el que consume la API).
- Cada servicio se probó de forma aislada con sus tests.

## Lección aprendida

La migración de un monolito a microservicios rara vez necesita el aparato completo de canary/shadow cuando el frontend y el backend se evolucionan **juntos** (mismo equipo, mismo ciclo). El estrangulamiento fase por fase sirvió, pero el corte definitivo se hizo en un paso porque pudimos alinear la API de golpe. Si mañana se migra otro sistema, evalúa si necesitas el canary o si un parity de endpoints bien testeado es suficiente.

## Qué sigue

La documentación operativa actual vive en [DEPLOY.md](./DEPLOY.md) y los runbooks de despliegue. La arquitectura del sistema actual está en [ARCHITECTURE.md](./ARCHITECTURE.md).
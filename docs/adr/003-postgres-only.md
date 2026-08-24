# ADR-003: PostgreSQL como única base de datos

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

El monolito ya usa PostgreSQL vía Prisma. Se necesita búsqueda full-text (reemplazo de
MeiliSearch), colas confiables y datos relacionales. Presupuesto restringido.

## Decisión

Un único motor **PostgreSQL** para todo:

- Búsqueda full-text: `tsvector` + índices GIN (elimina MeiliSearch del stack).
- Colas de trabajo: tablas `FOR UPDATE SKIP LOCKED` (patrón heredado del Automation Engine).
- Caché/calores: Redis (vía Floci RESP local / ElastiCache en prod) solo cuando el perfil
  de latencia lo exija.
- Una BD lógica por servicio (`etribunal_identity`, `etribunal_core`) — nunca esquema compartido
  entre servicios (regla anti-acoplamiento del plan).

## Consecuencias

- Un solo motor que operar, backup uniforme (`pg_dump`).
- RDS en producción; Floci RDS proxy (rango :7001-7099, una instancia por servicio) en local — mismo wire protocol.
- Si algún día un dominio exige otro motor, la decisión se toma por servicio vía ADR nuevo.

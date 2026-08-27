# ADR-005: Quartz JDBC ahora, Temporal.io diferido a Fase 3+

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

El AI Daily Activity Engine (cron + colas + recovery) hoy vive en el monolito con
`@Cron` y colas en memoria con claims en PostgreSQL. Temporal resuelve orquestación
durable pero añade un plano de control entero (server + workers + UI).

## Decisión

1. **Fase 2:** migrar la lógica del engine a `ai-engine-service` usando **Quartz con
   JobStore JDBC** sobre PostgreSQL (`FOR UPDATE SKIP LOCKED`), replicando el patrón
   claim/resume/stale-recovery ya probado en producción.
2. **Fase 3+ (opcional):** evaluar **Temporal 1.25.x** solo si los workflows crecen en
   complejidad (multi-día, compensaciones, señales human-in-the-loop).

## Consecuencias

- Fase inicial sin infraestructura extra; mismo motor de BD.
- El código de jobs queda aislado tras una interfaz (`AutomationOrchestrator`), de modo que
  la adopción futura de Temporal sea un cambio de adapter, no de dominio.

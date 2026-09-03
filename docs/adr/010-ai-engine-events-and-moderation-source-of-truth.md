# ADR-010: Fuente de verdad de moderación y eventos de actividad del AI Engine

**Estado:** Aceptado · **Fecha:** 2026-09-03

## Contexto

El AI Daily Activity Engine (ai-engine-service) crea casos y ejecuta interacciones
(comentarios, respuestas, reacciones, votos) escribiendo directamente en la BD compartida
(datos gestionados por core-domain-service). Al no pasar por los controllers de dominio,
el resto del sistema no se entera de la actividad en tiempo real y no queda registro de
analytics.

Además existe ambigüedad sobre la fuente de verdad de moderación: el engine lee
`cases.moderation_status` directamente (polling en `CaseGenerator`), pero también hay un
consumer Kafka (`ModerationConsumer`) que mantiene un mapa en memoria con resultados de
`moderation-tasks`.

## Decisión

1. **Fuente de verdad de moderación = `cases.moderation_status` (BD).**
   - El motor decide si un caso es publicable leyendo el campo en la BD tras el insert
     (`CaseGenerator.pollModeration`). El `ModerationConsumer` (ConcurrentHashMap) es un
     scaffold de integración futura, no se consulta para decidir publicación.
   - Kafka `moderation-tasks` queda como mejora a integrar en Fase 5 (garantías) cuando
     exista un moderador asíncrono real; el fallback local por polling es lo que rige hoy.

2. **Eventos de actividad publicados por el engine (best-effort).**
   - Nuevo `AutomationEventPublisher`: al crear un caso publica `CaseCreatedEvent` en
     `case-events`; al ejecutar comentarios/respuestas, reacciones y votos publica eventos
     ligeros en `comment-events`, `reaction-events` y `vote-events` respectivamente.
   - El publisher es no-bloqueante: si Kafka está caído, el flujo continúa (solo loguea).
     El engine ya persiste en BD, que es la fuente de verdad; Kafka es notificación.

3. **Registro de analytics.**
   - Nuevo `AnalyticsRecorder`: inserta en `interaction_logs` (mismas acciones que el
     dominio: VOTE/COMMENT/REACTION) tras cada interacción bot exitosa. Best-effort y con
     `metadata` indicando `source=ai-engine`. Sirve de semilla para el feedback loop de
     engagement (Fase 1 de AI_ACTIVITY_ENGINE_CLIENT_2.0).

## Consecuencias

- El sistema sí observa la actividad generada por IA a través de eventos Kafka y analytics,
  sin acoplar ai-engine a los controllers de core-domain.
- Publicar/registrar son best-effort; no degradan la ejecución si infraestructura falla.
- La integración de moderación asíncrona real (consumir `moderation-tasks` para decidir)
  queda explícitamente diferida y documentada, evitando dobles fuentes de verdad.

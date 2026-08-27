# ADR-002: Kafka self-hosted (Strimzi) como backbone de eventos

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

Los microservicios necesitan desacoplamiento por eventos. El presupuesto es mínimo
(fase inicial <50 usuarios → objetivo 1k DAU). Se descartó SQS/SNS por coste por
request y vendor lock-in; se evaluó también RabbitMQ.

## Decisión

- **Kafka en modo KRaft**, sin ZooKeeper.
- Local: broker Kafka propio (KRaft single-node en compose, Fase 1). La instalación actual de
  Floci (v1.6.0) no expone MSK.
- K8s/EKS: **Strimzi operator** con 3 brokers.
- Contratos: JSON envelope `DomainEvent` (eventId/eventType/occurredAt/version/
  correlationId/causationId/payload). Topics estables definidos en `common-kafka/Topics`.
- Idempotencia de consumidores por `eventId` en tabla dedup (Redis TTL + PK).

## Consecuencias

- Sin coste variable por mensaje.
- Operación propia (parcheo de brokers, rebalanceos) asumida por el equipo.
- La migración futura a MSK gestionado sería drop-in (mismo protocolo).

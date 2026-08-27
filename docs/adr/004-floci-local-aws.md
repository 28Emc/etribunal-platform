# ADR-004: Floci como emulador AWS local

**Estado:** Aceptado · **Fecha:** 2026-08-21

## Contexto

El despliegue objetivo es EKS + RDS + ElastiCache + MSK. Desarrollar contra la nube real
es lento y costoso; LocalStack Community quedó corto (72 servicios en Floci vs ~15) y
LocalStack Pro es pago por seat.

## Decisión

**Floci** (open source, MIT) como emulador AWS para desarrollo y tests:

| Servicio AWS | Puerto Floci | Uso |
|---|---|---|
| Edge | 4566 | Cualquier API AWS |
| RDS proxy | **7001–7099** | Wire PostgreSQL — un puerto por instancia, obtener vía `DescribeDBInstances` |

Redis y Kafka NO se emulan en esta instalación de Floci (v1.6.0): Redis usa el
standalone del host (`:6379`, con `requirepass`); Kafka se difiere a Fase 1.

**Instancias RDS creadas** (credenciales patrón `[proyecto]_user/_pass/_db`):

| Identificador | DB | Puerto | Usuario |
|---|---|---|---|
| `etribunal-identity-local` | etribunal_identity | 7002 | etribunal_user |
| `etribunal-core-local` | etribunal_core | 7003 | etribunal_user |

En CI corre como service container del workflow; en Testcontainers vía `common-test/FlociContainer`
(con reutilización activada). En producción se usan los servicios AWS reales sin cambios de código
(solo endpoints/credenciales).

## Consecuencias

- Paridad dev ≈ prod con coste cero.
- Riesgo: drift entre Floci y AWS real en edge cases → mitigar con smoke tests contra staging.
- Riesgo conocido: el catálogo de instancias RDS puede perderse al reiniciar Floci
  (volúmenes sobreviven, metadata no) → recrear instancias apuntando a los mismos puertos si ocurre.
- Imagen `floci/floci:1.6.0` fijada por tag (incluye fix #1480 del proxy PostgreSQL).

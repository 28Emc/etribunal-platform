# ADR-004: Floci como emulador AWS local

**Estado:** Aceptado · **Fecha:** 2026-08-21 · **Actualizado:** 2026-09-09 (persistencia)

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
- **Persistencia (2026-09):** en el modo Docker, Floci se levanta con `FLOCI_STORAGE_MODE=hybrid`
  y el volumen `floci-data` montado en `/app/data`. La metadata del catálogo RDS
  (`/app/data/rds-instances.json`) **persiste entre `up`/`down`**, al igual que cada PostgreSQL
  hermano en su volumen `floci-rds-{volumeId}`. Verificado: un ciclo down/up completo reutiliza
  los mismos IDs de instancia y data. El riesgo "el catálogo RDS se pierde al reiniciar" queda
  **mitigado** en Docker; solo `docker compose down -v` lo resetea. En CI (service container)
  sigue siendo fresco por definición, y en Testcontainers la reutilización queda dentro del ciclo
  del runner.
  - **Nota de alcance:** esta mitigación y el `docker-compose.yml` que la implementa son **solo
    para el entorno de desarrollo local**. Producción se despliega y configura por otra vía
    (aún no documentada); este ADR describe el flujo local/CI/test.
- **Seeds automáticos:** identity-service aplica en arranque `V5__seed_admin.sql`
  (admin `admin@etribunal.com / Admin@2026`, rol `ADMIN`) y `V6__seed_bots.sql`
  (25 usuarios bot para el AI Activity Engine). Ambas idempotentes.
- Imagen `floci/floci:1.6.0` fijada por tag (incluye fix #1480 del proxy PostgreSQL).

# Runbook: Despliegue a producción

> **Qué es esto:** el procedimiento para llevar el backend a **producción** con el flujo real actual. La orquestación Kubernetes/Helm, si algún día llega, estará en [deploy-k8s-future](./deploy-k8s-future.md).

## Requisitos previos

- **Staging validado** durante 24h+ sin issues críticos.
- Load test aprobado en staging (k6, escenario de 1k DAU).
- Backup de base de datos **completado y verificado**.
- Equipo notificado y alguien de guardia disponible.

## Checklist previo

- [ ] Staging estable 24h+.
- [ ] Load test OK (p99 < 500ms, error rate < 0.1%).
- [ ] Backup de DB hecho y verificado.
- [ ] Plan de rollback revisado con el equipo.
- [ ] Secrets rotados (JWT/INTERNAL_API_KEY no son los de dev).
- [ ] Scan de seguridad (Trivy, Snyk) sin blockers.

## Pasos

### 1. Backup de DB pre-deploy

```bash
pg_dump -h <prod-db-host> -U etribunal_user -d etribunal_core > prod_backup_$(date +%Y%m%d_%H%M%S).sql
```

### 2. Compilar y construir imágenes

```bash
./gradlew bootJar
docker build -f services/gateway-service/Dockerfile -t etribunal/gateway-service:prod-<sha> .
# ... repetir para identity, core-domain y ai-engine
```

### 3. Aplicar migraciones

```bash
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate
```

### 4. Levantar servicios en orden

Orden recomendado para minimizar impacto:

1. **Core e Identity primero** (necesitan DB y son el dominio).
2. **Gateway al final** (el borde — acorta la ventana de cambio visto desde fuera).
3. **AI Engine** opcional al último.

Cada servicio con sus variables de entorno de producción (ver `DEPLOY.md`).

### 5. Validar post-deploy

```bash
curl -s https://api.etribunal.com/actuator/health | jq .
curl -s https://api.etribunal.com/api/cases?take=1 | jq '.data | length'

# Smoke tests
./gradlew :tests:e2e:test -De2e.enabled=true -Dgateway.url=https://api.etribunal.com
```

### 6. Feature flags gradual (si aplica)

Si un release trae features nuevas, se activan progresivamente revisando métricas en cada paso (10% → 50% → 100%).

## Rollback

### Application rollback (< 5 min)

Re-desplegar el **artefacto anterior** (tag/JAR previo) reiniciando contenedores. Como compartes el mismo artefacto entre entornos, es simplemente "volver a correr lo anterior".

### Database rollback (SOLO si la migración causó problemas)

```bash
# 1. Parar el servicio afectado
# 2. Restaurar el backup
pg_restore -h <prod-db-host> -U etribunal_user -d etribunal_core prod_backup_*.sql
# 3. Re-desplegar
```

## Post-deploy

- [ ] Monitorear dashboards 1 hora.
- [ ] Revisar error rates, latencia, throughput.
- [ ] Feature flags en 100% (o el objetivo).
- [ ] Actualizar deployment tracker.
- [ ] Notificar al equipo.
- [ ] Retro/postmortem si hubo issues.
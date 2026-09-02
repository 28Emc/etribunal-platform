# Runbook: Deploy con Kubernetes/Helm (ruta futura)

> **⚠️ NO aplica todavía.** Este runbook conserva las **plantillas** que algún día usaremos cuando el sistema crezca y necesite orquestación Kubernetes. Hoy el deploy real es con Docker Compose / JARs y variables de entorno (ver [DEPLOY.md](../DEPLOY.md)). No ejecutes estos comandos: el directorio `infra/` **no existe** aún en el repositorio.
>
> Se mantiene como referencia de diseño para cuando llegue el momento, y para no perder el trabajo de pensar cómo se hará.

## Qué implica esta ruta

Cuando adoptemos Kubernetes, la idea es:

- Cada servicio tiene su **Helm chart** (`infra/helm/<servicio>`) con `values.yaml` para cada entorno (`values-dev`, `values-staging`, `values-prod`).
- La **migración Flyway** corre como un **Kubernetes Job** (`infra/kubernetes/base/flyway-migration-job.yaml`) *antes* de desplegar core-domain.
- Los despliegues son **rolling updates** con health checks y zero-downtime.
- Los secrets (`JWT_ACCESS_SECRET`, `INTERNAL_API_KEY`, DB, etc.) van en **Secrets de Kubernetes** (o un secret manager), nunca en valores.

### Estructura de directorios que se creará

```
infra/
├── helm/
│   ├── gateway-service/     (values-dev/staging/prod.yaml)
│   ├── identity-service/    ...
│   ├── core-domain-service/ ...
│   └── ai-engine-service/   ...
└── kubernetes/
    ├── base/
    │   └── flyway-migration-job.yaml
    └── overlays/
        ├── dev/
        └── staging/
            └── kustomization.yaml
```

---

## Deplegar a staging (cuando exista)

### Requisitos previos

- Imágenes Docker build y pusheadas a un registry.
- `kubectl` configurado para el cluster de staging.
- Helm 3.x instalado.
- Acceso a la DB para migraciones.

### Checklist previo al deploy

- [ ] CI en verde (unit, integration, contract).
- [ ] Imágenes etiquetadas `staging-<git-sha>`.
- [ ] Migración Flyway revisada (`validate` pasa).
- [ ] Variables actualizadas en `values-staging.yaml`.
- [ ] Feature flags listos para canary (si aplica).

### Pasos

```bash
# 1. Infraestructura (si hace falta)
cd infra/kubernetes/overlays/staging
kustomize build | kubectl apply -f -

# 2. Migraciones Flyway vía Kubernetes Job
kubectl apply -f infra/kubernetes/base/flyway-migration-job.yaml -n staging

# 3. Deploy servicios (rolling update)
helm upgrade --install identity   ./infra/helm/identity-service   -n staging -f ./infra/helm/identity-service/values-staging.yaml
helm upgrade --install core-domain ./infra/helm/core-domain-service -n staging -f ./infra/helm/core-domain-service/values-staging.yaml
helm upgrade --install ai-engine  ./infra/helm/ai-engine-service  -n staging -f ./infra/helm/ai-engine-service/values-staging.yaml
helm upgrade --install gateway    ./infra/helm/gateway-service    -n staging -f ./infra/helm/gateway-service/values-staging.yaml

# 4. Verificar
kubectl get pods -n staging -w
kubectl logs -n staging -l app=identity-service --tail=50
```

### Rollback (staging)

```bash
helm rollback identity 1 -n staging
helm rollback core-domain 1 -n staging
helm rollback ai-engine 1 -n staging
helm rollback gateway 1 -n staging
kubectl rollout status deployment/identity-service -n staging
```

---

## Desplegar a producción (cuando exista)

### Requisitos previos

- Staging validado 24h+ sin issues críticos.
- Load test en staging aprobado (k6, 1k DAU).
- Backup de DB completado (`pg_dump`).
- Equipo notificado y on-call disponible.

### Checklist previo al deploy

- [ ] Staging estable 24h+.
- [ ] Load test OK (p99 < 500ms, error rate < 0.1%).
- [ ] Backup de DB verificado.
- [ ] Plan de rollback revisado con el equipo.
- [ ] Feature flags en 0% para lo nuevo.
- [ ] Scan de seguridad (Trivy, Snyk).

### Pasos

```bash
# 1. Backup de DB pre-deploy
pg_dump -h <prod-db-host> -U etribunal_user -d etribunal_core > prod_backup_$(date +%Y%m%d_%H%M%S).sql

# 2. Deploy gateway primero (zero-downtime)
helm upgrade --install gateway ./infra/helm/gateway-service -n production -f ./infra/helm/gateway-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> --wait --timeout 10m

# 3. Deploy identity
helm upgrade --install identity ./infra/helm/identity-service -n production -f ./infra/helm/identity-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> --wait --timeout 10m

# 4. Migraciones core (Kubernetes Job)
kubectl apply -f infra/kubernetes/base/flyway-migration-job.yaml -n production
kubectl wait --for=condition=complete job/flyway-migration -n production --timeout=5m

# 5. Deploy core
helm upgrade --install core-domain ./infra/helm/core-domain-service -n production -f ./infra/helm/core-domain-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> --wait --timeout 10m

# 6. Deploy ai-engine
helm upgrade --install ai-engine ./infra/helm/ai-engine-service -n production -f ./infra/helm/ai-engine-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> --wait --timeout 10m

# 7. Feature flags gradual (10% → 50% → 100%)
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"10"}}'
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"50"}}'
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"100"}}'

# 8. Validación post-deploy
curl -s https://api.etribunal.com/actuator/health | jq .
curl -s https://api.etribunal.com/api/cases?take=1 | jq '.data | length'
```

### Rollback (producción)

```bash
# Application rollback (< 5 min)
helm rollback gateway 1 -n production
helm rollback identity 1 -n production
helm rollback core-domain 1 -n production
helm rollback ai-engine 1 -n production

# Database rollback SOLO si la migración causó problemas
kubectl scale deployment core-domain-service --replicas=0 -n production
pg_restore -h <prod-db-host> -U etribunal_user -d etribunal_core prod_backup_*.sql
kubectl scale deployment core-domain-service --replicas=3 -n production
```

---

## Checklist post-deploy

- [ ] Monitorear dashboards 1 hora.
- [ ] Revisar error rates, latencia, throughput.
- [ ] Feature flags en 100%.
- [ ] Actualizar deployment tracker.
- [ ] Notificar al equipo.
- [ ] Postmortem/retro si hubo issues.
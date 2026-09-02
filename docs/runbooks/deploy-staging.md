# Runbook: Deploy to Staging

## Prerequisites
- Docker images built and pushed to registry
- `kubectl` configured for staging cluster
- Helm 3.x installed
- Access to Floci/Cloud SQL for DB migrations

## Pre-deploy Checklist
- [ ] All CI checks pass (unit, integration, contract tests)
- [ ] Docker images tagged with `staging-<git-sha>`
- [ ] Database migration reviewed (Flyway `validate` passes)
- [ ] Environment variables updated in `values-staging.yaml`
- [ ] Feature flags configured for canary (if applicable)

## Deploy Steps

### 1. Deploy Infrastructure (if needed)
```bash
cd infra/kubernetes/overlays/staging
kustomize build | kubectl apply -f -
```

### 2. Run Database Migrations
```bash
# Flyway via Kubernetes Job (vía recomendada para staging)
kubectl apply -f infra/kubernetes/base/flyway-migration-job.yaml -n staging
```

### 3. Deploy Services (Rolling Update)
```bash
# Identity
helm upgrade --install identity ./infra/helm/identity-service -n staging -f ./infra/helm/identity-service/values-staging.yaml

# Core Domain
helm upgrade --install core-domain ./infra/helm/core-domain-service -n staging -f ./infra/helm/core-domain-service/values-staging.yaml

# AI Engine
helm upgrade --install ai-engine ./infra/helm/ai-engine-service -n staging -f ./infra/helm/ai-engine-service/values-staging.yaml

# Gateway
helm upgrade --install gateway ./infra/helm/gateway-service -n staging -f ./infra/helm/gateway-service/values-staging.yaml
```

### 4. Verify Deployment
```bash
# Check pod status
kubectl get pods -n staging -w

# Check health endpoints
kubectl exec -n staging deploy/identity-service -- curl -s localhost:8081/api/actuator/health
kubectl exec -n staging deploy/core-domain-service -- curl -s localhost:8082/api/actuator/health
kubectl exec -n staging deploy/ai-engine-service -- curl -s localhost:8083/actuator/health
kubectl exec -n staging deploy/gateway-service -- curl -s localhost:8080/actuator/health

# Check logs
kubectl logs -n staging -l app=identity-service --tail=50
```

### 5. Smoke Tests
```bash
# Run staging smoke tests
./gradlew :tests:e2e:test -Penvironment=staging
```

## Rollback Procedure
```bash
# Quick rollback (Helm)
helm rollback identity 1 -n staging
helm rollback core-domain 1 -n staging
helm rollback ai-engine 1 -n staging
helm rollback gateway 1 -n staging

# Verify rollback
kubectl rollout status deployment/identity-service -n staging
```

## Post-deploy
- [ ] Update deployment tracker with version
- [ ] Notify team in #deployments channel
- [ ] Monitor dashboards for 15 minutes
- [ ] Run load test if major release
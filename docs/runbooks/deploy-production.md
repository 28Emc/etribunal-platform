# Runbook: Deploy to Production

## Prerequisites
- Staging validated for 24h+ with zero critical issues
- Load test passed on staging (k6, 1k DAU scenario)
- Database backup completed (pg_dump)
- Team notified in #deployments channel
- On-call engineer available

## Pre-deploy Checklist
- [ ] Staging stable for 24h+
- [ ] Load test passed (p99 < 500ms, error rate < 0.1%)
- [ ] DB backup completed and verified
- [ ] Rollback plan reviewed with team
- [ ] Feature flags set to 0% for new features
- [ ] Security scan passed (Trivy, Snyk)

## Deploy Steps

### 1. Pre-deploy DB Backup
```bash
# On production DB instance
pg_dump -h <prod-db-host> -U etribunal_user -d etribunal_core > prod_backup_$(date +%Y%m%d_%H%M%S).sql
# Verify backup
pg_restore --list prod_backup_*.sql | head -20
```

### 2. Deploy Gateway (First - zero-downtime)
```bash
helm upgrade --install gateway ./infra/helm/gateway-service -n production -f ./infra/helm/gateway-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> \
  --wait --timeout 10m
```

### 3. Deploy Identity Service
```bash
helm upgrade --install identity ./infra/helm/identity-service -n production -f ./infra/helm/identity-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> \
  --wait --timeout 10m
```

### 4. Run DB Migrations (Core Domain)
```bash
# Flyway migration via Kubernetes Job
kubectl apply -f infra/kubernetes/base/flyway-migration-job.yaml -n production
kubectl wait --for=condition=complete job/flyway-migration -n production --timeout=5m

# Verify migration
kubectl logs job/flyway-migration -n production
```

### 5. Deploy Core Domain
```bash
helm upgrade --install core-domain ./infra/helm/core-domain-service -n production -f ./infra/helm/core-domain-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> \
  --wait --timeout 10m
```

### 5. Deploy AI Engine
```bash
helm upgrade --install ai-engine ./infra/helm/ai-engine-service -n production -f ./infra/helm/ai-engine-service/values-prod.yaml \
  --set image.tag=prod-<git-sha> \
  --wait --timeout 10m
```

### 6. Enable Feature Flags (Gradual)
```bash
# Start with 10% canary
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"10"}}'

# Monitor for 30 minutes
# If OK, increase to 50%
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"50"}}'

# Monitor for 1 hour
# If OK, increase to 100%
kubectl patch configmap feature-flags -n production -p '{"data":{"NEW_FEATURE_PERCENTAGE":"100"}}'
```

### 7. Post-deploy Validation
```bash
# Health checks
curl -s https://api.etribunal.com/actuator/health | jq .
curl -s https://api.etribunal.com/api/cases?take=1 | jq '.data | length'

# Smoke tests
./gradlew :tests:e2e:test -Penvironment=production

# Quick load test (5 min)
k6 run tests/k6/load-test.js -e BASE_URL=https://api.etribunal.com/api
```

## Rollback Procedure

### Application Rollback (< 5 min)
```bash
# Rollback all services
helm rollback gateway 1 -n production
helm rollback identity 1 -n production
helm rollback core-domain 1 -n production
helm rollback ai-engine 1 -n production

# Verify
kubectl rollout status deployment/gateway -n production
kubectl rollout status deployment/identity -n production
```

### Database Rollback (if migration applied)
```bash
# ONLY if migration caused issues
# 1. Scale down services
kubectl scale deployment core-domain-service --replicas=0 -n production

# 2. Restore DB from backup
pg_restore -h <prod-db-host> -U etribunal_user -d etribunal_core prod_backup_*.sql

# 3. Scale up
kubectl scale deployment core-domain-service --replicas=3 -n production
```

## Post-deploy
- [ ] Monitor dashboards for 1 hour
- [ ] Check error rates, latency, throughput
- [ ] Verify feature flags at 100%
- [ ] Update deployment tracker
- [ ] Send deployment notification
- [ ] Schedule post-deploy retrospective (if issues)
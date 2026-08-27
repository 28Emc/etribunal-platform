# Runbook: Incident Response

## Severity Levels

| Level | Description | Response Time | Escalation |
|-------|-------------|---------------|------------|
| **SEV-1** | Complete outage, data loss, security breach | 15 min | Page on-call + team lead |
| **SEV-2** | Major degradation, API errors > 5%, latency p99 > 2s | 30 min | Page on-call |
| **SEV-3** | Minor degradation, non-critical feature broken | 2 hours | Assign to team |
| **SEV-4** | Low priority, cosmetic, docs | Next sprint | Create ticket |

## Incident Response Flow

```
Alert Triggered
       │
       ▼
On-call acknowledges (15 min)
       │
       ▼
Assess severity (SEV-1/2/3/4)
       │
       ▼
Create incident channel (#incident-<date>-<id>)
       │
       ▼
Communicate status (status page, #incidents)
       │
       ▼
Investigate & mitigate
       │
       ▼
Resolve & verify
       │
       ▼
Close incident + postmortem (if SEV-1/2)
```

## Common Scenarios

### High Error Rate (5xx > 1%)
```bash
# 1. Check which service
kubectl get pods -n production -o wide

# 2. Check logs
kubectl logs -n production -l app=core-domain-service --tail=100 | grep -i error

# 3. Check dependencies
kubectl exec -n production deploy/core-domain-service -- curl -s localhost:8082/actuator/health

# 4. Quick mitigation
# - Scale up if resource exhaustion
kubectl scale deployment core-domain-service --replicas=5 -n production

# - Rollback if recent deploy
helm rollback core-domain 1 -n production
```

### High Latency (p99 > 2s)
```bash
# 1. Check DB connections
kubectl exec -n production deploy/core-domain-service -- curl -s localhost:8082/actuator/metrics/hikaricp.connections.active

# 2. Check Kafka lag
kubectl exec -n production deploy/ai-engine-service -- curl -s localhost:8083/actuator/metrics/kafka.consumer.lag

# 3. Check Redis
kubectl exec -n production deploy/identity-service -- redis-cli info memory

# 4. Mitigation
# - Increase DB pool size
# - Scale consumers
# - Clear cache if stale data
```

### Database Issues
```bash
# Check connections
psql -h <db-host> -U etribunal_user -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"

# Check long-running queries
psql -h <db-host> -U etribunal_user -c "SELECT pid, now() - pg_stat_activity.query_start AS duration, query FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC LIMIT 10;"

# Kill long queries if needed
psql -h <db-host> -U etribunal_user -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE now() - query_start > interval '5 minutes';"
```

### Kafka Consumer Lag
```bash
# Check lag
kubectl exec -n production deploy/ai-engine-service -- curl -s localhost:8083/actuator/metrics/kafka.consumer.lag

# Restart consumer group
kubectl rollout restart deployment/ai-engine-service -n production

# Increase consumer instances
kubectl scale deployment ai-engine-service --replicas=5 -n production
```

### Memory/CPU Exhaustion
```bash
# Check resource usage
kubectl top pods -n production

# Check JVM metrics
kubectl exec -n production deploy/core-domain-service -- curl -s localhost:8082/actuator/metrics/jvm.memory.used

# Mitigation
# - Increase heap: -Xmx2g in JAVA_OPTS
# - Scale horizontally
kubectl scale deployment core-domain-service --replicas=6 -n production

# - Restart pods gracefully
kubectl rollout restart deployment/core-domain-service -n production
```

## Communication Templates

### Initial Alert
```
🚨 INCIDENT: <title>
Severity: SEV-<1-4>
Service: <affected service>
Impact: <user-facing impact>
Status: Investigating
Lead: @oncall
Channel: #incident-<date>-<id>
```

### Status Update (every 30 min)
```
📊 UPDATE: <title>
Status: <Investigating/Mitigating/Monitoring/Resolved>
Progress: <what's been done>
ETA: <estimated resolution>
```

### Resolution
```
✅ RESOLVED: <title>
Root Cause: <brief>
Fix: <what was done>
Postmortem: <link> (if SEV-1/2)
```

## Postmortem Template (SEV-1/2)

### Incident Summary
- **Date**: YYYY-MM-DD
- **Duration**: X hours Y minutes
- **Severity**: SEV-1/2
- **Services Affected**: <list>

### Timeline
| Time (UTC) | Event |
|------------|-------|
| HH:MM | Alert triggered |
| HH:MM | On-call acknowledged |
| HH:MM | Root cause identified |
| HH:MM | Fix deployed |
| HH:MM | Verified resolved |

### Root Cause
<5 whys analysis>

### Impact
- Users affected: <number>
- Requests failed: <number>
- Revenue impact: <if applicable>

### Action Items
| Item | Owner | Due Date | Status |
|------|-------|----------|--------|
| Fix root cause | <name> | YYYY-MM-DD | 🔴/🟡/🟢 |
| Add monitoring | <name> | YYYY-MM-DD | 🔴/🟡/🟢 |
| Improve runbook | <name> | YYYY-MM-DD | 🔴/🟡/🟢 |

### Lessons Learned
<what went well, what didn't, what to improve>

## Contact Escalation

| Role | Name | Phone | Slack |
|------|------|-------|-------|
| Primary On-call | <name> | <phone> | @user |
| Secondary On-call | <name> | <phone> | @user |
| Team Lead | <name> | <phone> | @user |
| Engineering Manager | <name> | <phone> | @user |
| DB Admin | <name> | <phone> | @user |
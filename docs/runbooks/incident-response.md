# Runbook: Respuesta a incidentes

> **Para qué sirve:** qué hacer cuando algo se cae en eTribunal, ordenado por severidad. Está escrito para el flujo actual (contenedores Docker / JARs + variables de entorno, ver [DEPLOY.md](../DEPLOY.md)). Si algún día hay Kubernetes, lo migraremos para usar `kubectl`/`helm` — hoy no aplica.

## Niveles de severidad

| Nivel | Descripción | Tiempo de respuesta | Escalación |
|-------|-------------|---------------------|------------|
| **SEV-1** | Caída total, pérdida de datos, brecha de seguridad | 15 min | Alertar on-call + lead |
| **SEV-2** | Degradación mayor, errores de API > 5%, p99 > 2s | 30 min | Alertar on-call |
| **SEV-3** | Degradación menor, funcionalidad no crítica rota | 2 horas | Asignar al equipo |
| **SEV-4** | Baja prioridad, cosmético, docs | Siguiente sprint | Crear ticket |

## Flujo de respuesta

```
Alerta disparada
       │
       ▼
On-call confirma (15 min)
       │
       ▼
Evaluar severidad (SEV-1/2/3/4)
       │
       ▼
Crear canal de incidente (#incident-<fecha>-<id>)
       │
       ▼
Comunicar estado (status page, #incidents)
       │
       ▼
Investigar y mitigar
       │
       ▼
Resolver y verificar
       │
       ▼
Cerrar incidente + postmortem (si SEV-1/2)
```

## Escenarios comunes

### Tasa alta de errores (5xx > 1%)

```bash
# 1. ¿Qué servicio falla? Health checks directos
curl -s localhost:8080/actuator/health          # Gateway
curl -s localhost:8081/api/actuator/health      # Identity
curl -s localhost:8082/api/actuator/health      # Core Domain
curl -s localhost:8083/actuator/health          # AI Engine

# 2. Logs del servicio afectado (contenedor o JAR)
docker logs --tail 200 <servicio-contenedor>
journalctl -u etribunal-core -n 200 --no-pager   # si corre como systemd

# 3. Mitigación rápida
# - Reinicio suave si es degradación
./gradlew :services:core-domain-service:bootRun   # (o reiniciar el contenedor)
# - Rollback si fue un deploy reciente (ver rollback en DEPLOY.md)
```

### Alta latencia (p99 > 2s)

```bash
# 1. Conexiones de DB en uso
psql -h <db-host> -U etribunal_user -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"

# 2. Memoria Redis
redis-cli -a <password> --no-auth-warning info memory

# 3. Métricas JVM (si metrics endpoint activo)
curl -s localhost:8082/api/actuator/metrics/jvm.memory.used

# 4. Mitigación
# - Aumentar pool de conexiones DB
# - Limpiar caché si hay datos stale
# - Escalar horizontalmente (más instancias, mismas env vars)
```

### Problemas de base de datos

```bash
# Conexiones activas
psql -h <db-host> -U etribunal_user -c "SELECT count(*) FROM pg_stat_activity WHERE state = 'active';"

# Queries largas
psql -h <db-host> -U etribunal_user -c "SELECT pid, now()-pg_stat_activity.query_start AS duration, query FROM pg_stat_activity WHERE state = 'active' ORDER BY duration DESC LIMIT 10;"

# Matar queries largas si es necesario
psql -h <db-host> -U etribunal_user -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE now()-query_start > interval '5 minutes';"
```

### Consumo de memoria/CPU

```bash
# Uso de recursos (Docker)
docker stats

# Métricas JVM
curl -s localhost:8082/api/actuator/metrics/jvm.memory.used

# Mitigación
# - Aumentar heap: -Xmx1g en JAVA_OPTS (o -XX:MaxRAMPercentage)
# - Escalar horizontalmente (más instancias)
# - Reinicio gradual/graceful (server.shutdown=graceful ya está en el Dockerfile)
```

### Tasa de error de requests (5xx correlacionada con AI Engine)

```bash
# Revisar si un run de automatización falló (no bloquea el tráfico normal)
curl -s -H "x-sysadmin-api-key: <key>" localhost:8083/automation/status

# Si AI Engine está caído, el resto sigue: reintentar o reiniciar el servicio
```

## Plantillas de comunicación

### Alerta inicial

```
🚨 INCIDENTE: <título>
Severidad: SEV-<1-4>
Servicio: <servicio afectado>
Impacto: <impacto visible al usuario>
Estado: Investigando
Lead: @oncall
Canal: #incident-<fecha>-<id>
```

### Actualización de estado (cada 30 min)

```
📊 ACTUALIZACIÓN: <título>
Estado: <Investigando/Mitigando/Monitoreando/Resuelto>
Progreso: <qué se ha hecho>
ETA: <resolución estimada>
```

### Resolución

```
✅ RESUELTO: <título>
Causa raíz: <breve>
Fix: <qué se hizo>
Postmortem: <enlace> (si SEV-1/2)
```

## Plantilla de postmortem (SEV-1/2)

### Resumen del incidente
- **Fecha**: YYYY-MM-DD
- **Duración**: X horas Y minutos
- **Severidad**: SEV-1/2
- **Servicios afectados**: <lista>

### Cronología
| Hora (UTC) | Evento |
|------------|--------|
| HH:MM | Alerta disparada |
| HH:MM | On-call confirmó |
| HH:MM | Causa raíz identificada |
| HH:MM | Fix desplegado |
| HH:MM | Verificado resuelto |

### Causa raíz
<análisis de los 5 porqués>

### Impacto
- Usuarios afectados: <número>
- Requests fallidos: <número>
- Impacto en ingresos: <si aplica>

### Acciones
| Item | Owner | Fecha límite | Estado |
|------|-------|--------------|--------|
| Fix causa raíz | <nombre> | YYYY-MM-DD | 🔴/🟡/🟢 |
| Añadir monitoreo | <nombre> | YYYY-MM-DD | 🔴/🟡/🟢 |
| Mejorar runbook | <nombre> | YYYY-MM-DD | 🔴/🟡/🟢 |

### Lecciones aprendidas
<qué salió bien, qué no, qué mejorar>

## Escalación de contacto

| Rol | Nombre | Teléfono | Slack |
|-----|--------|----------|-------|
| On-call primario | <nombre> | <teléfono> | @user |
| On-call secundario | <nombre> | <teléfono> | @user |
| Team Lead | <nombre> | <teléfono> | @user |
| Engineering Manager | <nombre> | <teléfono> | @user |
| DB Admin | <nombre> | <teléfono> | @user |
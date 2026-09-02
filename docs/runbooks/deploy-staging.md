# Runbook: Despliegue a staging

> **Qué es esto:** el procedimiento para llevar el backend a un entorno de **staging** (pre-producción) con el flujo real actual (imágenes Docker / JARs + variables de entorno). Si en el futuro adoptamos Kubernetes, el despliegue pasará a [deploy-k8s-future](./deploy-k8s-future.md).

## Requisitos previos

- El código está en una rama estable (p. ej. `develop` tras mergear features).
- Las imágenes Docker (o JARs) se pudieron construir localmente con `./gradlew bootJar`.
- Tienes acceso al host de staging y a su base de datos.

## Checklist previo

- [ ] CI en verde (unit + tests de servicio + e2e si aplica).
- [ ] Imágenes versionadas con un tag (p. ej. `staging-<git-sha>`).
- [ ] Migración Flyway revisada.
- [ ] Variables de entorno de staging preparadas (DB, Redis, S3, secrets).

## Pasos

### 1. Compilar y construir

```bash
./gradlew bootJar
docker build -f services/gateway-service/Dockerfile -t etribunal/gateway-service:staging .
# ... repetir para identity, core-domain y ai-engine (ver DEPLOY.md)
```

### 2. Publicar imágenes

Pushea las imágenes a tu registry de staging (ECR, GHCR, etc.) con el tag de la rama.

### 3. Aplicar migraciones

```bash
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate
```

Apuntándolas a la DB de staging (sobreescribe `SPRING_DATASOURCE_URL` según corresponda).

### 4. Levantar servicios

Levanta cada contenedor en el host de staging con las variables de entorno de staging. El mismo artefacto que corre en local corre aquí: solo cambia lo que le inyectas.

### 5. Verificar

```bash
curl http://<staging-host>:8080/actuator/health       # Gateway
curl http://<staging-host>:8081/api/actuator/health   # Identity
curl http://<staging-host>:8082/api/actuator/health   # Core Domain
curl http://<staging-host>:8083/actuator/health       # AI Engine
```

### 6. Smoke tests

```bash
./gradlew :tests:e2e:test -De2e.enabled=true -Dgateway.url=http://<staging-host>:8080
```

## Rollback

Volver a desplegar la **versión anterior** (mismo tag/JAR previo):

```bash
# Contenedores: reiniciar con el tag anterior
docker compose up -d --no-deps <servicio>
```

> Las migraciones no se revierten solas: si una causó problemas, restaura el backup de DB antes de re-desplegar.

## Post-deploy

- [ ] Actualizar el tracker de despliegues con la versión.
- [ ] Avisar al equipo.
- [ ] Monitorear dashboards 15 minutos.
- [ ] Correr load test si es un release importante.
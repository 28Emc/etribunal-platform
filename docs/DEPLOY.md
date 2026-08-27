# Deploy Guide

## Entornos

| Entorno | Propósito | URL |
|---------|-----------|-----|
| Local | Desarrollo | localhost |
| Staging | Testing pre-producción | (configurar) |
| Producción | Usuarios reales | (configurar) |

## Docker

### Construir imágenes

```bash
# Desde la raíz del monorepo
./gradlew bootJar

# Construir imágenes individuales
docker build -f services/gateway-service/Dockerfile -t etribunal/gateway-service .
docker build -f services/identity-service/Dockerfile -t etribunal/identity-service .
docker build -f services/core-domain-service/Dockerfile -t etribunal/core-domain-service .
docker build -f services/ai-engine-service/Dockerfile -t etribunal/ai-engine-service .
```

### Docker Compose

```bash
# Desarrollo local
docker compose up -d                              # Solo infra
docker compose --profile app up -d                # Infra + servicios

# Producción (ver sección Variables de entorno)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Dockerfiles

Todos los servicios usan la misma estructura:

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY services/{service}/build/libs/*.jar app.jar
USER app
EXPOSE {port}
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Optimizaciones para producción:**
- Multi-stage build (compilar en Gradle, solo copiar JAR)
- Layers de Docker para cache de dependencias
- Health checks en Docker Compose

## Variables de entorno

### Gateway Service

| Variable | Requerida | Default | Descripción |
|----------|-----------|---------|-------------|
| `JWT_ACCESS_SECRET` | Sí | — | Secret para validar access tokens |
| `JWT_ISSUER` | No | `etribunal` | Issuer claim |
| `REDIS_HOST` | No | `localhost` | Host de Redis |
| `REDIS_PORT` | No | `6379` | Puerto de Redis |
| `REDIS_PASSWORD` | No | (empty) | Password de Redis |
| `MIGRATION_ENABLED` | No | `false` | Habilitar Strangler Fig |
| `NESTJS_URL` | No | `http://localhost:3001/api` | URL del backend legacy |
| `CANARY_ENABLED` | No | `false` | Habilitar canary routing |
| `CANARY_DEFAULT_PCT` | No | `0` | Porcentaje default canary |
| `SHADOW_ENABLED` | No | `false` | Habilitar shadow traffic |

### Identity Service

| Variable | Requerida | Default | Descripción |
|----------|-----------|---------|-------------|
| `JWT_ACCESS_SECRET` | Sí | — | Secret para access tokens |
| `JWT_REFRESH_SECRET` | Sí | — | Secret para refresh tokens |
| `JWT_ISSUER` | No | `etribunal` | Issuer claim |
| `JWT_ACCESS_TTL` | No | `PT15M` | TTL access token |
| `JWT_REFRESH_TTL` | No | `P7D` | TTL refresh token |
| `INTERNAL_API_KEY` | Sí | — | Token para comunicación interna |
| `SPRING_DATASOURCE_URL` | Sí | — | JDBC URL de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Sí | — | Usuario de DB |
| `SPRING_DATASOURCE_PASSWORD` | Sí | — | Password de DB |
| `REDIS_HOST` | No | `localhost` | Host de Redis |
| `REDIS_PASSWORD` | No | (empty) | Password de Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker |

### Core Domain Service

| Variable | Requerida | Default | Descripción |
|----------|-----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | Sí | — | JDBC URL de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Sí | — | Usuario de DB |
| `SPRING_DATASOURCE_PASSWORD` | Sí | — | Password de DB |
| `IDENTITY_BASE_URL` | Sí | — | URL interna de identity-service |
| `INTERNAL_API_KEY` | Sí | — | Token para comunicación interna |
| `FRONTEND_URL` | No | `http://localhost:3000` | URL del frontend (invite links) |
| `S3_ENDPOINT` | Sí | — | URL de S3/LocalStack |
| `AWS_REGION` | No | `us-east-1` | Región AWS |
| `AWS_ACCESS_KEY_ID` | Sí | — | Access key de S3 |
| `AWS_SECRET_ACCESS_KEY` | Sí | — | Secret key de S3 |
| `S3_BUCKET` | No | `etribunal-media` | Bucket de S3 |
| `REDIS_HOST` | No | `localhost` | Host de Redis |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker |

### AI Engine Service

| Variable | Requerida | Default | Descripción |
|----------|-----------|---------|-------------|
| `CORE_DB_HOST` | Sí | — | Host de PostgreSQL |
| `CORE_DB_PORT` | No | `5432` | Puerto de PostgreSQL |
| `CORE_DB_NAME` | Sí | — | Nombre de la DB |
| `CORE_DB_USER` | Sí | — | Usuario de DB |
| `CORE_DB_PASS` | Sí | — | Password de DB |
| `AI_API_KEY` | Sí | — | Google AI Studio API key |
| `AI_MODEL` | No | `gemini-2.0-flash` | Modelo Gemini |
| `AUTOMATION_ENABLED` | No | `false` | Master switch automatización |
| `AUTOMATION_DRY_RUN` | No | `true` | Solo planificar, no ejecutar |
| `KAFKA_BOOTSTRAP_SERVERS` | No | `localhost:9092` | Kafka broker |

## CI/CD

### GitHub Actions (pendiente)

Pipeline sugerido:

```yaml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew build
      - run: ./gradlew test

  docker:
    needs: build
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew bootJar
      - run: docker compose build
      # Push a container registry (ECR, GHCR, etc.)
```

### Build stages

```
1. Compile     → ./gradlew compileJava
2. Test        → ./gradlew test
3. Package     → ./gradlew bootJar
4. Docker      → docker compose build
5. Deploy      → docker compose up -d
```

## Producción

### Checklist pre-deploy

- [ ] Variables de entorno configuradas (secrets, no defaults)
- [ ] `JWT_ACCESS_SECRET` y `JWT_REFRESH_SECRET` rotados (no defaults de dev)
- [ ] `INTERNAL_API_KEY` configurado y coincidente en identity + core
- [ ] PostgreSQL accesible y migraciones aplicadas
- [ ] Redis accesible
- [ ] S3 bucket creado y con políticas de acceso
- [ ] Kafka broker corriendo (si se usa)
- [ ] CORS configurado para el dominio real
- [ ] Rate limits ajustados
- [ ] SpringDoc deshabilitado (`SPRINGDOC_SWAGGER_UI_ENABLED=false`)
- [ ] Health checks configurados

### Health checks

```bash
# Gateway
curl http://localhost:8080/actuator/health

# Identity
curl http://localhost:8081/actuator/health

# Core Domain
curl http://localhost:8082/actuator/health

# AI Engine
curl http://localhost:8083/actuator/health
```

### Monitoreo

| Métrica | Endpoint |
|---------|----------|
| Health | `/actuator/health` |
| Info | `/actuator/info` |
| Prometheus | `/actuator/prometheus` |

### Rotación de secrets

```bash
# Generar nuevos secrets
openssl rand -hex 64   # Para JWT secrets (64 bytes hex)
openssl rand -hex 32   # Para INTERNAL_API_KEY (32 bytes hex)

# Actualizar en todos los servicios
export JWT_ACCESS_SECRET="<nuevo>"
export JWT_REFRESH_SECRET="<nuevo>"
export INTERNAL_API_KEY="<nuevo>"
```

## Rollback

```bash
# Detener servicios
docker compose --profile app down

# Revertir a versión anterior
git checkout v1.0.0
./gradlew bootJar
docker compose --profile app up -d --build
```

## Backup de base de datos

```bash
# Identity DB
pg_dump -h localhost -p 7002 -U etribunal_user etribunal_identity > identity_backup.sql

# Core DB
pg_dump -h localhost -p 7003 -U etribunal_user etribunal_core > core_backup.sql

# Restaurar
psql -h localhost -p 7002 -U etribunal_user etribunal_identity < identity_backup.sql
```

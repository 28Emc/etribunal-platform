# Guía de despliegue

> **En una frase:** el backend sale a producción como contenedores Docker (o JARs sobre una máquina cualquiera). Hoy no hay Kubernetes — el despliegue con Helm que verás en este repo es una **plantilla para el futuro**, documentada aparte en [runbooks/deploy-k8s-future](./runbooks/deploy-k8s-future.md).

Esta guía cubre el flujo **real** que se usa hoy en día. Todo depende de variables de entorno: los servicios en sí no llevan credenciales ni datasources hardcodeados, así que el mismo JAR / imagen sirve para local, staging o producción — solo cambia lo que le inyectas.

---

## El panorama en 30 segundos

Tienes 4 servicios Spring Boot en un monorepo Gradle. Para ponerlos a correr en cualquier entorno:

1. Compilas fat-jars con `./gradlew bootJar`.
2. (Opcional) Construyes imágenes Docker desde los `Dockerfile` de cada servicio.
3. Levantas cada uno con las **variables de entorno** correctas (DB, Redis, S3, secrets).
4. Aplicas migraciones Flyway.
5. El gateway `:8080` es la única puerta: expone `/api/**` y rutea al resto.

Ningún servicio conoce "dónde vive"; se lo dices tú con el entorno. Eso hace que desplegar sea repetible y predecible.

---

## Qué construye y qué necesita cada servicio

Detalle de variables en las tablas más abajo. En resumen:

| Servicio | DB | Redis | S3 | Kafka* | Otros |
|----------|----|-------|----|--------|-------|
| `gateway-service` | — | sí | — | — | — |
| `identity-service` | `etribunal_identity` | sí | — | no | `INTERNAL_API_KEY` |
| `core-domain-service` | `etribunal_core` | sí | sí | opcional | `INTERNAL_API_KEY`, `IDENTITY_BASE_URL` |
| `ai-engine-service` | `etribunal_core` | — | — | opcional | `AI_API_KEY` |

> \*Kafka es **best-effort**: si el broker no está, los servicios siguen funcionando (la producción de eventos no bloquea). Solo activas los flujos de media (core) y automatización (ai-engine) cuando hay broker.

---

## 1. Compilar

```bash
# Desde la raíz del monorepo
./gradlew bootJar

# Los fat-jars quedan en services/<servicio>/build/libs/*.jar
```

Compilar con la toolchain de Gradle se encarga del JDK 21 automáticamente (vía Foojay).

---

## 2. Construir imágenes Docker (opcional)

Cada servicio tiene su propio `Dockerfile` (basado en `eclipse-temurin:21-jre`):

```bash
docker build -f services/gateway-service/Dockerfile      -t etribunal/gateway-service .
docker build -f services/identity-service/Dockerfile     -t etribunal/identity-service .
docker build -f services/core-domain-service/Dockerfile  -t etribunal/core-domain-service .
docker build -f services/ai-engine-service/Dockerfile    -t etribunal/ai-engine-service .
```

> El `Dockerfile` solo copia el JAR compilado (no compila dentro). Por eso **siempre** ejecuta `./gradlew bootJar` antes de `docker build`.

---

## 3. Variables de entorno

### Gateway Service

| Variable | Obligatoria | Default | Qué es |
|----------|-------------|---------|--------|
| `JWT_ACCESS_SECRET` | Sí | — | Secret para validar los access tokens en el edge |
| `JWT_ISSUER` | No | `etribunal` | Claim `iss` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | No | `localhost`/`6379`/— | Redis (sesión/rate-limit) |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:3000` | Orígenes permitidos |

### Identity Service

| Variable | Obligatoria | Default | Qué es |
|----------|-------------|---------|--------|
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | Sí | — | Firma de tokens (64 bytes hex) |
| `JWT_ISSUER` | No | `etribunal` | Claim `iss` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | No | `PT15M` / `P7D` | Duración de tokens |
| `INTERNAL_API_KEY` | Sí | — | Secret de comunicación interna (debe coincidir con core) |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Sí | — | PostgreSQL `etribunal_identity` |
| `REDIS_HOST` / `REDIS_PASSWORD` | No | `localhost` / — | Redis |

### Core Domain Service

| Variable | Obligatoria | Default | Qué es |
|----------|-------------|---------|--------|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Sí | — | PostgreSQL `etribunal_core` |
| `IDENTITY_BASE_URL` | Sí | — | URL interna de identity (ej. `http://identity-service:8081`) |
| `INTERNAL_API_KEY` | Sí | — | Debe coincidir con identity |
| `FRONTEND_URL` | No | `http://localhost:3000` | Usada en invites/links |
| `S3_ENDPOINT` / `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` | Sí | `us-east-1` | S3 (o LocalStack/Floci) |
| `S3_BUCKET` | No | `etribunal-media` | Bucket de media |
| `REDIS_HOST` | No | `localhost` | Redis |

### AI Engine Service

| Variable | Obligatoria | Default | Qué es |
|----------|-------------|---------|--------|
| `CORE_DB_HOST` / `CORE_DB_PORT` / `CORE_DB_NAME` / `CORE_DB_USER` / `CORE_DB_PASS` | Sí | `5432` | PostgreSQL compartido `etribunal_core` |
| `AI_API_KEY` | Sí | — | Google AI Studio key |
| `AI_MODEL` | No | `gemini-2.0-flash` | Modelo Gemini |
| `AUTOMATION_ENABLED` | No | `false` | Master switch de automatización |
| `AUTOMATION_DRY_RUN` | No | `true` | `true` = planifica sin ejecutar |

> **Regla de oro:** `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` e `INTERNAL_API_KEY` deben **rotarse** respecto a los valores de desarrollo — nunca uses los defaults en producción.

---

## 4. Aplicar migraciones

Flyway se encarga del schema. Las tareas Gradle apuntan por defecto a los puertos locales de Floci:

```bash
./gradlew :services:identity-service:flywayMigrate
./gradlew :services:core-domain-service:flywayMigrate
```

En producción, aplica las migraciones apuntando a tu PostgreSQL real. El propio arranque de cada servicio también ejecuta Flyway automáticamente al iniciar.

---

## 5. Levantar los servicios

Cada servicio arranca con `java -jar app.jar`, inyectando las variables de entorno correspondientes. Dos formas típicas:

**A) Contenedores**: usando las imágenes del paso 2, con un `docker compose` propio (el `docker-compose.yml` del repo es para desarrollo; en prod defines el tuyo con los envs reales) o directamente `docker run`.

**B) Proceso simple**: en cualquier máquina con JRE 21:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/etribunal_core \
SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
java -jar services/core-domain-service/build/libs/core-domain-service.jar
```

Repite el patrón para los 4 servicios. El orden típico: identity y core primero (necesitan DB), luego gateway (el borde), y ai-engine opcional al final.

---

## 6. Verificar el desplegable

```bash
curl http://<host>:8080/actuator/health        # Gateway
curl http://<host>:8081/api/actuator/health    # Identity (context-path /api)
curl http://<host>:8082/api/actuator/health    # Core Domain (context-path /api)
curl http://<host>:8083/actuator/health        # AI Engine
```

Deberías obtener `{"status":"UP",...}` en todos.

---

## Entornos

| Entorno | Cómo llega | Comentarios |
|---------|------------|-------------|
| **Local** | Gradle `bootRun` con perfil `local` (ver README) | Usa Floci + envs de dev |
| **Staging** | Imágenes Docker con envs de staging | Antes de producción |
| **Producción** | Imágenes Docker / JARs con envs reales | Secrets rotados |

> Hoy el repo no trae scripts ni composición para staging/producción: se despliega con las variables de entorno elegidas al levantar. En el futuro, cuando llegue Kubernetes, se usará lo de [runbooks/deploy-k8s-future](./runbooks/deploy-k8s-future.md).

---

## Rollback

Como compartes el mismo JAR entre entornos y solo cambias el entorno, un rollback es:

```bash
# Volver a la versión anterior (misma imagen/tag previo reiniciando el contenedor)
docker compose up -d --no-deps <servicio>   # apuntando al tag anterior

# O, con JARs: restaurar el .jar anterior y reiniciar el proceso
```

Nada atómico: solo reinicia con el artefacto anterior. Las migraciones Flyway son la parte que no se revierte sola — por eso `pg_dump` antes de una migración de schema.

---

## Backups de base de datos

```bash
# Identity
pg_dump -h <host> -p 7002 -U etribunal_user etribunal_identity > identity_backup.sql

# Core
pg_dump -h <host> -p 7003 -U etribunal_user etribunal_core > core_backup.sql

# Restaurar
psql -h <host> -p 7002 -U etribunal_user etribunal_identity < identity_backup.sql
```
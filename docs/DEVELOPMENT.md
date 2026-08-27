# Development Guide

## Setup local

### Prerrequisitos

```bash
# Verificar JDK
java -version   # JDK 21+ (Gradle auto-provisiona Temurin 21 si difiere)

# Verificar Docker
docker --version   # Docker Desktop corriendo
```

### 1. Clonar e instalar

```bash
git clone https://github.com/28Emc/etribunal-platform.git
cd etribunal-platform
./gradlew build   # Compila todo + corre tests
```

### 2. Infraestructura local

```bash
docker compose up -d
```

Esto levanta:
- **Floci** (LocalStack): `:4566` (S3), `:7002` (identity DB), `:7003` (core DB)
- **Redis**: `:6379` (password: `eYVX7EwVmmxKPCDmwMtyKVge8oLd2t81`)

### 3. Crear instancias RDS en Floci

```bash
# Identity DB
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-identity-local \
  --db-name etribunal_identity \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20

# Core DB
aws --endpoint-url http://localhost:4566 rds create-db-instance \
  --db-instance-identifier etribunal-core-local \
  --db-name etribunal_core \
  --master-username etribunal_user \
  --master-user-password etribunal_pass \
  --engine postgres \
  --db-instance-class db.t3.micro \
  --allocated-storage 20
```

> Las instancias tardan ~30s en estar disponibles. Los puertos son 7002 (identity) y 7003 (core).

### 4. Aplicar migraciones

```bash
# Identity
cd services/identity-service
flyway migrate -url=jdbc:postgresql://localhost:7002/etribunal_identity \
  -user=etribunal_user -password=etribunal_pass \
  -locations=filesystem:src/main/resources/db/migration

# Core
cd services/core-domain-service
flyway migrate -url=jdbc:postgresql://localhost:7003/etribunal_core \
  -user=etribunal_user -password=etribunal_pass \
  -locations=filesystem:src/main/resources/db/migration
```

### 5. Levantar servicios

Abrir 4 terminales separadas:

```bash
# Terminal 1: Gateway
./gradlew :services:gateway-service:bootRun --args='--spring.profiles.active=local'

# Terminal 2: Identity
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'

# Terminal 3: Core Domain
./gradlew :services:core-domain-service:bootRun --args='--spring.profiles.active=local'

# Terminal 4: AI Engine (opcional)
./gradlew :services:ai-engine-service:bootRun --args='--spring.profiles.active=local'
```

### 6. Verificar

```bash
# Health checks
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Identity
curl http://localhost:8082/actuator/health   # Core Domain

# Registrar usuario
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testjudge","email":"test@test.com","password":"Test1234!","displayName":"Test"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!"}'
```

## Docker Compose

### Perfiles

```bash
docker compose up -d                           # Solo infra (Floci + Redis)
docker compose --profile app up -d             # Infra + 4 servicios Spring
docker compose --profile temporal up -d        # + Temporal + UI
docker compose --profile app --profile temporal up -d  # Todo
```

### Construir servicios

```bash
./gradlew bootJar                              # Compila todos los servicios
docker compose --profile app up -d --build     # Reconstruye y levanta
```

## Testing

### Unit + Integration tests

```bash
./gradlew test                                 # Todos
./gradlew :services:identity-service:test      # Solo identity
./gradlew :services:core-domain-service:test   # Solo core
```

### E2E tests

Los E2E tests hacen llamadas HTTP reales a servicios corriendo:

```bash
# Terminal 1: Levantar servicios
docker compose --profile app up -d

# Terminal 2: Correr E2E
./gradlew :tests:e2e:test -De2e.enabled=true -Dgateway.url=http://localhost:8080
```

### Test profiles

Cada servicio tiene `application-test.yml` que usa H2 in-memory:

```bash
# Tests con H2 (sin Floci)
./gradlew :services:identity-service:test -Dspring.profiles.active=test
```

## Debugging

### IntelliJ IDEA

1. Run → Edit Configurations → Gradle
2. Task: `:services:identity-service:bootRun`
3. Arguments: `--args='--spring.profiles.active=local'`
4. Activate: ✅ Override parameters → `spring.profiles.active=local`

### VS Code

```bash
# Con Java Extension Pack instalado
# Abrir la carpeta del servicio específico
# F5 → Launch: Spring Boot

# O manualmente con breakpoints
./gradlew :services:identity-service:bootRun \
  --args='--spring.profiles.active=local' \
  -Dorg.gradle.jvmargs='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005'
```

### Logs

```bash
# Ver logs en tiempo real (cuando corren via bootRun)
# Los logs salen en la terminal de bootRun

# Logging levels configurables en application-local.yml
logging:
  level:
    com.etribunal: DEBUG
    org.springframework.security: DEBUG
```

## Hot Reload

Spring Boot DevTools no está configurado (no es recomendado en monorepos Gradle). Alternativas:

- **bootRun**: Se reinicia automáticamente al detectar cambios en `src/main/java`
- **IntelliJ**: Build → Build Project (Ctrl+F9) + Enable Auto-Compile
- **VS Code**: Java Language Server auto-compila

## Floci (LocalStack)

### Comandos útiles

```bash
# Listar instancias RDS
aws --endpoint-url http://localhost:4566 rds describe-db-instances

# Listar buckets S3
aws --endpoint-url http://localhost:4566 s3 ls

# Crear bucket de media
aws --endpoint-url http://localhost:4566 s3 mb s3://etribunal-media

# Verificar salud
curl http://localhost:4566/_localstack/health
```

### Credenciales

| Variable | Valor |
|----------|-------|
| `AWS_ACCESS_KEY_ID` | `test` |
| `AWS_SECRET_ACCESS_KEY` | `test` |
| `AWS_REGION` | `us-east-1` |
| `S3_ENDPOINT` | `http://localhost:4566` |

## Git workflow

```bash
# Crear feature branch
git checkout develop
git checkout -b feature/my-feature

# Trabajar y commitear
git add -A && git commit -m "feat: description"

# Push y PR
git push -u origin feature/my-feature
# Crear PR en GitHub: feature/my-feature → develop

# Merge a develop (después de review)
# Deploy a main (después de testing en develop)
```

### Convenciones de commits

```
feat:     feature nueva
fix:      bug fix
docs:     documentación
refactor: refactoring sin cambio de comportamiento
test:     tests
chore:    build, CI, configuración
```

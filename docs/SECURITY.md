# Security

## Autenticación JWT

### Flujo

```
1. Register/Login
   POST /api/auth/register → { access_token, refresh_token, user }
   POST /api/auth/login    → { access_token, refresh_token, user }

2. Request autenticado
   Authorization: Bearer <access_token>

3. Refresh
   POST /api/auth/refresh → { access_token, refresh_token }

4. Logout
   POST /api/auth/logout → invalida refresh token
```

### Tokens

| Token | TTL | Almacenamiento | Uso |
|-------|-----|----------------|-----|
| Access | 15 minutos | Header `Authorization` | Acceso a endpoints protegidos |
| Refresh | 7 días | Body del request | Renovar par de tokens |

### Implementación

- **Algoritmo:** HS256 (HMAC-SHA256) via Nimbus JOSE+JWT
- **Secrets:** Dos secrets separados (`accessSecret`, `refreshSecret`), mínimo 32 bytes cada uno
- **Claims:** `sub` (UUID), `iss` (issuer), `typ` ("access"/"refresh"), `username`, `roles`, `iat`, `exp`, `jti`
- **Validación:** Firma + tipo + issuer + expiración (sin tolerancia de clock skew)

### Single-session model

Solo hay un refresh token activo por usuario. Al hacer refresh:
1. Se verifica el JTI del refresh token contra Redis (`auth:session:<userId>`)
2. Se elimina la sesión antigua
3. Se crea una nueva sesión con el nuevo JTI

Esto significa que login en otro dispositivo invalida la sesión anterior.

### Brute-force protection

- Redis key: `auth:attempts:<email>`
- Límite: 5 intentos fallidos
- Ventana: 15 minutos
- Respuesta: incluye `lockedForSeconds` cuando excede el límite

## Password policy

| Campo | Requisitos |
|-------|------------|
| Password | Mínimo 8, máximo 128 caracteres |
| | Al menos 1 mayúscula |
| | Al menos 1 dígito |
| Email | Formato válido, máx. 255 chars |
| Username | 5-12 caracteres, solo minúsculas/números/`_` |
| | No empieza ni termina con `_` |

Regex username: `^[a-záéíóúüñ][a-záéíóúüñ0-9_]{3,10}[a-záéíóúüñ0-9]$`

## Gateway JWT Filter

El gateway valida JWT en el edge y propag la identidad a servicios downstream:

```
Request → Gateway
  ├─ Extrae token del header Authorization
  ├─ Valida firma, expiración, issuer
  ├─ Inyecta headers:
  │   X-User-Id: <uuid>
  │   X-Username: <username>
  │   X-Roles: <role1,role2>
  └─ Reenvía al servicio destino
```

**Filtros de gateway:**
- `JwtGatewayFilter` (order=-10): validación JWT
- `CanaryRoutingFilter` (order=-5): routing Strangler Fig
- `ShadowTrafficFilter` (order=-3): duplicación para comparación

### Public paths (sin auth)

```
/api/auth/register
/api/auth/login
/api/auth/refresh
/api/users/search
/api/users/top-judges
/api/users/*
/api/users/*/followers
/api/users/*/following
/actuator/**
```

## Internal Token (service-to-service)

Los servicios se comunican internamente con un token estático:

```
CoreDomain → IdentityService
  Header: X-Internal-Token: <token>
```

- **Mecanismo:** SHA-256 hash + `MessageDigest.isEqual()` (timing-safe comparison)
- **No es JWT:** Es un API key estático, no tiene expiración
- **Configuración:** `INTERNAL_API_KEY` env var (debe coincidir en ambos servicios)
- **Dev default:** `dev-only-internal-token-1234`

### Endpoints internos

Solo accesibles vía `X-Internal-Token` (no expuestos vía gateway):

```
GET /users/internal/summaries?ids=uuid1,uuid2
GET /users/internal/following-ids
```

## Rate Limiting

Implementado con Spring Boot Starter + Redis:

| Endpoint | Límite | Ventana |
|----------|--------|---------|
| Login | 10 requests | 30 segundos |
| Refresh | 5 requests | 30 segundos |
| Register | 5 requests | 30 segundos |
| Check existence | 5 requests | 60 segundos |
| Resend verification | 3 requests | 60 segundos |

## CORS

Configurado en el gateway para el frontend:

```yaml
cors:
  allowed-origins: http://localhost:3000
  allowed-methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
  allowed-headers: "*"
  allow-credentials: true
  max-age: 86400
```

## Headers de seguridad

| Header | Valor | Implementado por |
|--------|-------|------------------|
| `X-Content-Type-Options` | `nosniff` | Helmet / Spring |
| `X-Frame-Options` | `DENY` | Helmet / Spring |
| `X-XSS-Protection` | `0` | Helmet / Spring |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Spring |

## Moderación

El sistema de moderación protege contra contenido malicioso:

### Texto
- Normalización: lowercase, unicode, leetspeak, caracteres repetidos
- Regex: links sospechosos, teléfonos, emails, doxxing, spam
- Diccionarios JSON: profanity, harassment, hate, violence, sexual, spam

### Imágenes
- MIME type whitelist: JPEG, PNG, GIF, WebP
- Tamaño máximo: 5MB
- Validación de extensión

### Estados de moderación

```
PENDING → APPROVED (contenido seguro)
         → FLAGGED (requiere revisión, risk_score > umbral)
         → REJECTED (contenido prohibido)
```

## Revocación de tokens

| Mecanismo | Alcance |
|-----------|---------|
| Logout | Elimina refresh token de Redis (single-session) |
| Refresh | Elimina sesión anterior, crea nueva |
| Brute-force lockout | Bloquea intentos por email |
| Soft delete | `deleted_at` en users table |

## Pendiente para producción

- [ ] Migrar de localStorage a httpOnly cookies (SameSite=Lax)
- [ ] CSRF tokens si se usan cookies
- [ ] Token blacklist (Redis TTL) para invalidación inmediata de access tokens
- [ ] Audit logging para operaciones sensibles
- [ ] WAF rules en load balancer

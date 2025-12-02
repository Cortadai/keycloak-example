# Keycloak Spring Boot + Angular - Patrón BFF (Headers + Redis)

## Descripción

Aplicación de demostración que implementa el **patrón Backend for Frontend (BFF)** con autenticación OAuth2 mediante Keycloak.

**Esta rama (`oauth2-bff-headers`)** usa Bearer tokens en headers HTTP con refresh tokens almacenados en Redis.

### Tecnologías Principales

- **Backend**: Spring Boot 3.x + Spring Security OAuth2
- **Frontend**: Angular 21 (Standalone Components + Signals)
- **Autenticación**: Keycloak (OAuth2 Authorization Code Flow)
- **Token Store**: Redis (refresh tokens y códigos temporales)
- **Seguridad**: Bearer tokens + refresh en Redis

---

## Arquitectura BFF con Headers

### ¿Qué es el patrón BFF?

El **Backend for Frontend** en esta versión:

1. El **frontend almacena accessToken en localStorage**
2. El **backend gestiona refresh tokens en Redis** (nunca salen al frontend)
3. Las peticiones llevan **header `Authorization: Bearer`**
4. El **backend valida tokens** y protege endpoints

### Flujo de Autenticación

```
┌─────────┐         ┌──────────────┐        ┌───────┐      ┌──────────┐
│ Angular │         │ Spring Boot  │        │ Redis │      │ Keycloak │
│  :4200  │         │    :8081     │        │ :6379 │      │  :9090   │
└────┬────┘         └──────┬───────┘        └───┬───┘      └────┬─────┘
     │                     │                    │               │
     │ 1. GET /api/auth/login                   │               │
     ├────────────────────>│                    │               │
     │                     │ 2. OAuth2 redirect │               │
     │                     ├────────────────────────────────────>
     │ 3. Login en Keycloak│                    │               │
     │<─────────────────────────────────────────────────────────┤
     │                     │                    │               │
     │ 4. Callback con auth code                │               │
     │                     │<───────────────────────────────────┤
     │                     │                    │               │
     │                     │ 5. Exchange code for tokens        │
     │                     ├───────────────────────────────────>│
     │                     │<───────────────────────────────────┤
     │                     │                    │               │
     │                     │ 6. Store temp_code │               │
     │                     │    (UUID, TTL 30s) │               │
     │                     ├───────────────────>│               │
     │                     │                    │               │
     │ 7. Redirect /callback?code=uuid          │               │
     │<────────────────────┤                    │               │
     │                     │                    │               │
     │ 8. POST /api/auth/exchange               │               │
     │    { code: uuid }   │                    │               │
     ├────────────────────>│                    │               │
     │                     │ 9. Get + Delete    │               │
     │                     │    temp_code       │               │
     │                     ├───────────────────>│               │
     │                     │<──────────────────┤               │
     │                     │                    │               │
     │                     │ 10. Store          │               │
     │                     │     refresh_token  │               │
     │                     ├───────────────────>│               │
     │                     │                    │               │
     │ 11. { accessToken, expiresIn }           │               │
     │<────────────────────┤                    │               │
     │                     │                    │               │
     │ 12. localStorage.set(accessToken)        │               │
     │                     │                    │               │
     │ 13. GET /api/user/me                     │               │
     │     Authorization: Bearer <JWT>          │               │
     ├────────────────────>│                    │               │
     │<────────────────────┤ User data          │               │
```

### Características de Seguridad

✅ **El frontend NUNCA ve el refresh token**
✅ **El client_secret NUNCA está en el frontend**
✅ **Código temporal de uso único** (TTL 30s, se elimina al usar)
✅ **Refresh proactivo** (antes de que expire) + reactivo (en 401)
✅ **Sesión única por usuario** (nuevo login invalida el anterior)
✅ **CORS simplificado** (sin credentials)

### Trade-offs vs Cookies HttpOnly

| Aspecto | Cookies HttpOnly | Headers + Redis |
|---------|-----------------|-----------------|
| XSS | Protegido | Expuesto (localStorage) |
| CSRF | Posible (mitigado con SameSite) | No aplica |
| Cross-domain | Complejo | Simple |
| API Gateway | Problemático | Compatible |
| Escalabilidad | Sesiones server-side | Stateless + Redis |

---

## Estructura del Proyecto

```
keycloak-spring-demo/
├── docker-compose.yml                    # Keycloak + Redis
├── src/main/java/com/example/keycloak/
│   ├── config/
│   │   ├── SecurityConfig.java           # STATELESS, Bearer tokens
│   │   ├── OAuth2LoginSuccessHandler.java # Genera código temporal
│   │   └── RedisConfig.java              # Configuración Redis
│   ├── controller/
│   │   ├── AuthController.java           # /exchange, /refresh, /logout
│   │   ├── UserController.java           # Endpoints para usuarios
│   │   ├── AdminController.java          # Endpoints para admins
│   │   └── PublicController.java         # Endpoints públicos
│   ├── service/
│   │   ├── TokenService.java             # CRUD Redis
│   │   └── KeycloakTokenService.java     # Refresh/Revoke en KC
│   ├── dto/                              # DTOs de request/response
│   └── model/
│       ├── UserInfo.java
│       └── TokenData.java                # Datos en Redis
│
├── frontend/src/app/
│   ├── core/
│   │   ├── models/user.model.ts          # Interfaces TypeScript
│   │   ├── services/auth.service.ts      # localStorage + refresh timer
│   │   ├── guards/auth.guard.ts          # Verificación local + backend
│   │   └── interceptors/auth.interceptor.ts # Bearer header + 401 retry
│   ├── features/
│   │   ├── login/                        # Componente de login
│   │   ├── callback/                     # Intercambio de código
│   │   └── dashboard/                    # Dashboard del usuario
│   ├── app.config.ts
│   └── app.routes.ts                     # Incluye ruta /callback
│
├── CLAUDE.md                             # Guía para Claude Code
└── README.md                             # Este archivo
```

---

## Inicio Rápido

### Prerequisitos

1. **Java 17+** y Maven instalados
2. **Node.js 18+** y npm instalados
3. **Docker** para Keycloak y Redis

### Paso 1: Levantar Infraestructura

```bash
docker-compose up -d
```

Esto levanta:
- **Keycloak** en `http://localhost:9090` (admin/admin)
- **Redis** en `localhost:6379`

### Paso 2: Configurar Keycloak

1. Acceder a `http://localhost:9090`
2. Login con admin/admin
3. Crear realm: `mi-realm`
4. Crear client: `spring-boot-client` (Confidential)
5. Configurar:
   - Valid Redirect URIs: `http://localhost:8081/*`
   - Web Origins: `http://localhost:4200`
6. Copiar el Client Secret a `application.yml`
7. Crear roles: `user`, `admin`
8. Crear usuario de prueba y asignar roles

### Paso 3: Ejecutar Backend

```bash
# Actualizar client-secret en application.yml si es necesario
mvn clean install
mvn spring-boot:run
```

Verificar: `http://localhost:8081/public/status`

### Paso 4: Ejecutar Frontend

```bash
cd frontend
npm install
npm start
```

Verificar: `http://localhost:4200`

### Paso 5: Probar

1. Navegar a `http://localhost:4200`
2. Click en "Login con Keycloak"
3. Autenticarse en Keycloak
4. Verificar que redirige a `/callback` y luego a `/dashboard`
5. Abrir DevTools → Application → Local Storage
6. Verificar `access_token` y `token_expiry`
7. Verificar en Redis: `docker exec -it redis redis-cli KEYS "*"`

---

## Endpoints de la API

### Autenticación

| Endpoint | Método | Acceso | Descripción |
|----------|--------|--------|-------------|
| `/api/auth/login` | GET | Público | Inicia OAuth2 con Keycloak |
| `/api/auth/exchange` | POST | Público | Intercambia código temporal por accessToken |
| `/api/auth/refresh` | POST | Público* | Renueva accessToken (requiere Bearer header) |
| `/api/auth/status` | GET | Público* | Verifica validez del Bearer token |
| `/api/auth/logout` | POST | Autenticado | Cierra sesión, revoca tokens |

*Requieren Bearer token para funcionar correctamente

### Usuario (Rol: USER)

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/user/me` | GET | Info del usuario autenticado |
| `/api/user/profile` | GET | Perfil completo |
| `/api/user/dashboard` | GET | Dashboard del usuario |

### Admin (Rol: ADMIN)

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/admin/dashboard` | GET | Panel de administración |
| `/api/admin/users` | GET | Lista de usuarios |
| `/api/admin/stats` | GET | Estadísticas del sistema |

### Público

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/public/hello` | GET | Endpoint de prueba |
| `/public/info` | GET | Información de la API |
| `/public/status` | GET | Health check |

---

## Configuración

### Puertos

- **Keycloak**: 9090
- **Spring Boot**: 8081
- **Angular**: 4200
- **Redis**: 6379

### Variables de Entorno

**Backend** (`application.yml`):
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

app:
  frontend:
    url: http://localhost:4200
  keycloak:
    revoke-uri: http://localhost:9090/realms/mi-realm/protocol/openid-connect/revoke
```

---

## Testing

### Verificar Redis

```bash
# Ver todas las claves
docker exec -it redis redis-cli KEYS "*"

# Ver un refresh token específico
docker exec -it redis redis-cli GET "refresh_token:{userId}"

# Ver TTL de una clave
docker exec -it redis redis-cli TTL "refresh_token:{userId}"
```

### Verificar Flujo Completo

1. Login → Verificar redirect a `/callback`
2. Verificar `access_token` en localStorage
3. Verificar `refresh_token:{userId}` en Redis
4. Esperar a que expire o forzar 401
5. Verificar refresh automático en console
6. Logout → Verificar localStorage vacío y Redis sin refresh token

---

## Troubleshooting

### Error: "Código temporal inválido o expirado"

- El código temporal tiene TTL de 30 segundos
- Verifica que Redis esté corriendo: `docker-compose ps`
- Revisa logs del backend para más detalles

### Error: "No se encontró refresh token"

- El usuario fue deslogueado o expiró la sesión
- Nuevo login en otra pestaña invalida el refresh token anterior
- Solución: Hacer login nuevamente

### 401 en todas las peticiones

- Verificar que el token esté en localStorage
- Verificar que el interceptor esté añadiendo el header
- Verificar logs del backend para errores de validación JWT

### CORS Errors

- Ya no se requiere `withCredentials: true`
- Verificar que el backend esté corriendo
- Verificar configuración CORS en `SecurityConfig.java`

---

## Comparación de Ramas

Este repositorio tiene dos implementaciones del patrón BFF:

| Rama | Transporte | Refresh Token | Sesión |
|------|-----------|---------------|--------|
| `oauth2-bff-cookies` | Cookies HttpOnly | En cookie | STATEFUL |
| `oauth2-bff-headers` | Authorization header | En Redis | STATELESS |

---

## Referencias

- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Angular Security Guide](https://angular.dev/best-practices/security)
- [BFF Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)
- [Redis Documentation](https://redis.io/documentation)

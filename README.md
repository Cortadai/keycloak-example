# Keycloak Spring Boot + Angular - Patron BFF con Binding (JWT + Cookie)

## Descripcion

Aplicacion de demostracion que implementa el **patron Backend for Frontend (BFF)** con autenticacion OAuth2 mediante Keycloak.

**Esta rama (`oauth2-bff-binding`)** implementa el patron "Llave Partida" que protege contra XSS y CSRF simultaneamente.

### Tecnologias Principales

- **Backend**: Spring Boot 3.x + Spring Security OAuth2 + JJWT
- **Frontend**: Angular 21 (Standalone Components + Signals)
- **Autenticacion**: Keycloak (OAuth2 Authorization Code Flow)
- **Token Store**: Redis (refresh tokens y codigos temporales)
- **Seguridad**: JWT con fingerprint + Cookie HttpOnly (binding)

---

## Arquitectura BFF con Binding

### El Patron "Llave Partida"

El binding requiere **AMBOS** para autenticarse:

```
+-----------------------------------------------------------+
|                                                           |
|   JWT en Header         +    Cookie HttpOnly              |
|   Authorization               Fingerprint                  |
|                                                           |
|   claim: fingerprint    ==   valor: SHA-256(fingerprint)  |
|   valor: "abc123..."         valor: "hash..."             |
|                                                           |
|         |                          |                      |
|         v                          v                      |
|    localStorage             Cookie Store                  |
|    (accesible por JS)       (HttpOnly = inaccesible JS)   |
|                                                           |
|   ========================================================|
|                                                           |
|   XSS roba el JWT    -> No tiene la cookie  -> BLOQUEADO  |
|   CSRF usa la cookie -> No tiene el JWT     -> BLOQUEADO  |
|                                                           |
+-----------------------------------------------------------+
```

### Flujo de Autenticacion con Binding

```
+---------+         +--------------+        +-------+      +----------+
| Angular |         | Spring Boot  |        | Redis |      | Keycloak |
|  :4200  |         |    :8081     |        | :6379 |      |  :9090   |
+----+----+         +------+-------+        +---+---+      +----+-----+
     |                     |                    |               |
     | 1. GET /api/auth/login                   |               |
     +---------------------->                   |               |
     |                     | 2. OAuth2 redirect |               |
     |                     +--------------------------------------->
     | 3. Login en Keycloak|                    |               |
     <----------------------------------------------------------+
     |                     |                    |               |
     | 4. Callback con auth code                |               |
     |                     <------------------------------------+
     |                     |                    |               |
     |                     | 5. Exchange code for tokens        |
     |                     +------------------------------------>
     |                     <------------------------------------+
     |                     |                    |               |
     |                     | 6. Store temp_code |               |
     |                     +------------------>|               |
     |                     |                    |               |
     | 7. Redirect /callback?code=uuid          |               |
     <---------------------+                    |               |
     |                     |                    |               |
     | 8. POST /api/auth/exchange               |               |
     |    { code: uuid }   |                    |               |
     |    (withCredentials)|                    |               |
     +---------------------->                   |               |
     |                     | 9. Get + Delete temp_code          |
     |                     +------------------>|               |
     |                     <-------------------+               |
     |                     |                    |               |
     |                     | 10. Generate fingerprint           |
     |                     |     hash = SHA-256(fingerprint)    |
     |                     |     JWT with claim fingerprint     |
     |                     |                    |               |
     |                     | 11. Store refresh_token            |
     |                     +------------------>|               |
     |                     |                    |               |
     | 12. Response:       |                    |               |
     |     Body: { accessToken, expiresIn }     |               |
     |     Set-Cookie: Fingerprint={hash}; HttpOnly             |
     <---------------------+                    |               |
     |                     |                    |               |
     | 13. localStorage.set(accessToken)        |               |
     |     Cookie se guarda automaticamente     |               |
     |                     |                    |               |
     | 14. GET /api/user/me                     |               |
     |     Authorization: Bearer <JWT>          |               |
     |     Cookie: Fingerprint={hash}           |               |
     +---------------------->                   |               |
     |                     | 15. Validate binding               |
     |                     |     SHA-256(jwt.fingerprint)==cookie?
     |                     |     -> Continue    |               |
     <---------------------+ User data          |               |
```

### Comparacion de las 3 Ramas

| Aspecto | cookies | headers | binding |
|---------|---------|---------|---------|
| JWT en | Cookie HttpOnly | localStorage | localStorage |
| Viaja como | Cookie automatica | Header Authorization | Header Authorization |
| Refresh token | Cookie HttpOnly | Solo Redis | Solo Redis |
| Cookie adicional | No | No | Fingerprint (hash) |
| Proteccion XSS | Total | Vulnerable | Binding |
| Proteccion CSRF | SameSite | Total | Total |
| CORS credentials | Si | No | Si |
| Complejidad | Baja | Media | Alta |
| Cross-domain | Dificil | Facil | Medio |

---

## Estructura del Proyecto

```
keycloak-spring-demo/
+-- docker-compose.yml
+-- src/main/java/com/example/keycloak/
|   +-- config/
|   |   +-- SecurityConfig.java           # JWT decoder propio, CORS credentials
|   |   +-- OAuth2LoginSuccessHandler.java # Extrae claims de Keycloak
|   |   +-- RedisConfig.java
|   +-- controller/
|   |   +-- AuthController.java           # exchange/refresh/logout con binding
|   |   +-- UserController.java
|   |   +-- AdminController.java
|   |   +-- PublicController.java
|   +-- service/
|   |   +-- TokenService.java             # CRUD Redis
|   |   +-- KeycloakTokenService.java     # Refresh/Revoke en KC
|   |   +-- FingerprintService.java       # Genera UUID, calcula SHA-256
|   |   +-- JwtService.java               # Genera JWT propio con fingerprint
|   +-- filter/
|   |   +-- FingerprintValidationFilter.java # Valida binding JWT+Cookie
|   +-- dto/
|   +-- model/
|       +-- UserInfo.java
|       +-- TokenData.java                # Incluye username, email, roles
|
+-- frontend/src/app/
|   +-- core/
|   |   +-- models/user.model.ts
|   |   +-- services/auth.service.ts
|   |   +-- guards/auth.guard.ts
|   |   +-- interceptors/auth.interceptor.ts # withCredentials: true
|   +-- features/
|   |   +-- login/
|   |   +-- callback/
|   |   +-- dashboard/
|   +-- app.config.ts
|   +-- app.routes.ts
|
+-- CLAUDE.md
+-- CLAUDE_CODE_INSTRUCTIONS_BINDING.md   # Especificacion detallada
+-- README.md
```

---

## Inicio Rapido

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
4. Verificar redirect a `/callback` y luego a `/dashboard`
5. Abrir DevTools:
   - **Application > Local Storage**: verificar `access_token`
   - **Application > Cookies**: verificar cookie `Fingerprint` (HttpOnly)
6. Verificar en Redis: `docker exec -it redis redis-cli KEYS "*"`

---

## Endpoints de la API

### Autenticacion

| Endpoint | Metodo | Acceso | Descripcion |
|----------|--------|--------|-------------|
| `/api/auth/login` | GET | Publico | Inicia OAuth2 con Keycloak |
| `/api/auth/exchange` | POST | Publico | Intercambia codigo por JWT + Cookie |
| `/api/auth/refresh` | POST | Publico* | Renueva JWT, rota fingerprint |
| `/api/auth/status` | GET | Publico* | Verifica validez del binding |
| `/api/auth/logout` | POST | Autenticado | Cierra sesion, elimina cookie |

*Requieren Bearer token + Cookie para funcionar correctamente

### Usuario (Rol: USER)

| Endpoint | Metodo | Descripcion |
|----------|--------|-------------|
| `/api/user/me` | GET | Info del usuario autenticado |
| `/api/user/profile` | GET | Perfil completo |
| `/api/user/dashboard` | GET | Dashboard del usuario |

### Admin (Rol: ADMIN)

| Endpoint | Metodo | Descripcion |
|----------|--------|-------------|
| `/api/admin/dashboard` | GET | Panel de administracion |
| `/api/admin/users` | GET | Lista de usuarios |
| `/api/admin/stats` | GET | Estadisticas del sistema |

### Publico

| Endpoint | Metodo | Descripcion |
|----------|--------|-------------|
| `/public/hello` | GET | Endpoint de prueba |
| `/public/info` | GET | Informacion de la API |
| `/public/status` | GET | Health check |

---

## Configuracion

### Puertos

- **Keycloak**: 9090
- **Spring Boot**: 8081
- **Angular**: 4200
- **Redis**: 6379

### Variables de Entorno

**Backend** (`application.yml`):
```yaml
app:
  frontend:
    url: http://localhost:4200

  jwt:
    secret: ${JWT_SECRET:mi-secret-super-seguro-para-jwt-binding}
    expiration: 900  # 15 minutos

  fingerprint:
    cookie-name: Fingerprint
    http-only: true
    secure: false    # true en produccion (HTTPS)
    same-site: Strict
    path: /
    max-age: 900     # Mismo TTL que el JWT
```

---

## Testing

### Verificar Binding

```bash
# Ver cookie en el navegador
DevTools -> Application -> Cookies -> localhost

# Probar SIN cookie (debe fallar)
curl -X GET http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {jwt}"
# Resultado: 401 - Binding requerido

# Probar CON cookie (debe funcionar)
curl -X GET http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {jwt}" \
  -H "Cookie: Fingerprint={hash}"
# Resultado: 200 - User data
```

### Verificar Redis

```bash
# Ver todas las claves
docker exec -it redis redis-cli KEYS "*"

# Ver un refresh token especifico
docker exec -it redis redis-cli GET "refresh_token:{userId}"

# Ver TTL de una clave
docker exec -it redis redis-cli TTL "refresh_token:{userId}"
```

### Verificar Flujo Completo

1. Login -> Verificar redirect a `/callback`
2. Verificar `access_token` en localStorage
3. Verificar cookie `Fingerprint` en DevTools (HttpOnly)
4. Verificar `refresh_token:{userId}` en Redis
5. Esperar a que expire o forzar 401
6. Verificar refresh automatico (fingerprint rota)
7. Logout -> Verificar localStorage vacio, cookie eliminada

---

## Troubleshooting

### Error: "Binding requerido - cookie faltante"

- Las cookies no estan viajando
- Verificar `withCredentials: true` en el interceptor Angular
- Verificar CORS `allowCredentials: true` en backend

### Error: "Binding invalido"

- El hash no coincide (posible token robado)
- El fingerprint fue rotado pero se uso el JWT anterior
- Solucion: Hacer login nuevamente

### Error: "Codigo temporal invalido o expirado"

- El codigo temporal tiene TTL de 30 segundos
- Verifica que Redis este corriendo: `docker-compose ps`
- Revisa logs del backend para mas detalles

### 401 en todas las peticiones

- Verificar que el token este en localStorage
- Verificar que la cookie `Fingerprint` exista
- Verificar logs del backend para errores de validacion

### CORS Errors

- Verificar que el backend este corriendo
- Verificar que `app.frontend.url` sea exactamente `http://localhost:4200`
- Con `allowCredentials: true`, no se puede usar `*` en origins

---

## Escenarios de Ataque Bloqueados

### Ataque XSS

```
Atacante ejecuta JS malicioso
    |
    v
Roba JWT de localStorage
    |
    v
Intenta usar JWT desde su maquina
    |
    +--- Header: Authorization: Bearer {jwt_robado}
    +--- Cookie: (NO TIENE - HttpOnly, otro dominio)
    |
    v
Servidor valida binding:
    SHA-256(jwt.fingerprint) != (cookie no existe)
    |
    v
401 UNAUTHORIZED - Ataque bloqueado
```

### Ataque CSRF

```
Victima visita pagina maliciosa
    |
    v
Request automatico
    |
    +--- Header: Authorization: (NO TIENE)
    +--- Cookie: Fingerprint={hash} (se envia automatico)
    |
    v
Servidor detecta:
    No hay header Authorization
    |
    v
401 UNAUTHORIZED - Ataque bloqueado
```

---

## Referencias

- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Angular Security Guide](https://angular.dev/best-practices/security)
- [BFF Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)
- [Token Binding](https://datatracker.ietf.org/doc/html/rfc8471)
- [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)

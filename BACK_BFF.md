# Backend - Spring Boot BFF con JWT Headers + Redis

Documentación completa del backend implementado con Spring Boot, Spring Security OAuth2 y Redis.

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Componentes Principales](#componentes-principales)
- [Configuración](#configuración)
- [Flujo de Autenticación](#flujo-de-autenticación)
- [Redis Storage](#redis-storage)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura

### Patrón BFF en el Backend

El backend implementa el patrón BFF (Backend for Frontend) donde:

1. **Actúa como OAuth2 Client** hacia Keycloak
2. **Actúa como Resource Server** para validar JWTs
3. **Gestiona tokens en Redis** (refresh tokens y códigos temporales)
4. **Valida roles** extraídos de Keycloak
5. **Protege endpoints** con Spring Security
6. **Sesiones STATELESS** - no usa sesiones HTTP

### Diagrama de Componentes

```
┌────────────────────────────────────────────────────────────┐
│              Spring Boot Application                        │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │           SecurityConfig (BFF STATELESS)             │ │
│  │  • CORS (sin credentials)                            │ │
│  │  • STATELESS sessions                                │ │
│  │  • OAuth2 Login + Resource Server (JWT)              │ │
│  │  • Bearer token authentication                       │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │         OAuth2LoginSuccessHandler                    │ │
│  │  • Recibe tokens de Keycloak                         │ │
│  │  • Genera código temporal (UUID)                     │ │
│  │  • Almacena en Redis (TTL 30s)                       │ │
│  │  • Redirige al frontend con código                   │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                            │
│  ┌────────────────────┐    ┌─────────────────────────┐   │
│  │   TokenService     │    │  KeycloakTokenService   │   │
│  │  • CRUD Redis      │    │  • Refresh tokens       │   │
│  │  • Temp codes      │    │  • Revoke tokens        │   │
│  │  • Refresh tokens  │    │  • Comunicación KC      │   │
│  └────────────────────┘    └─────────────────────────┘   │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐ │
│  │                   Controllers                        │ │
│  │  • AuthController (/exchange, /refresh, /logout)     │ │
│  │  • UserController (@PreAuthorize)                    │ │
│  │  • AdminController (@PreAuthorize)                   │ │
│  │  • PublicController                                  │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │     Redis       │
                    │  • temp_code:*  │
                    │  • refresh:*    │
                    └─────────────────┘
```

---

## Componentes Principales

### 1. SecurityConfig.java

**Ubicación**: `src/main/java/com/example/keycloak/config/SecurityConfig.java`

**Responsabilidades**:
- Configuración BFF con sessions STATELESS
- CORS configurado para Angular (sin credentials)
- Integración OAuth2 Login + Resource Server
- Extracción de roles de Keycloak

**Configuración clave**:

```java
// Session STATELESS - no cookies de sesión
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)

// CORS sin credentials (Bearer tokens en headers)
configuration.setAllowCredentials(false);

// OAuth2 Login con handler personalizado
.oauth2Login(oauth2 -> oauth2
    .successHandler(oauth2LoginSuccessHandler)
)

// Resource Server para validar JWT en headers
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwtAuthenticationConverter(jwtAuthenticationConverter())
    )
)
```

**Extracción de roles**:
- **Realm roles**: `realm_access.roles` → `ROLE_USER`, `ROLE_ADMIN`
- **Client roles**: `resource_access.{client}.roles` → `ROLE_*`

---

### 2. OAuth2LoginSuccessHandler.java

**Ubicación**: `src/main/java/com/example/keycloak/config/OAuth2LoginSuccessHandler.java`

**Responsabilidades**:
- Captura tokens después del login exitoso en Keycloak
- Genera código temporal UUID
- Almacena tokens en Redis con TTL de 30 segundos
- Redirige al frontend con el código temporal

**Flujo**:

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request,
                                    HttpServletResponse response,
                                    Authentication authentication) {
    // 1. Obtener tokens de Keycloak
    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
    OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

    // 2. Crear TokenData con toda la info
    TokenData tokenData = new TokenData(
        accessToken.getTokenValue(),
        refreshToken.getTokenValue(),
        userId,
        expiresIn
    );

    // 3. Generar código temporal y guardar en Redis (TTL 30s)
    String tempCode = tokenService.createTempCode(tokenData);

    // 4. Redirigir al frontend con el código
    response.sendRedirect(frontendUrl + "/callback?code=" + tempCode);
}
```

**¿Por qué código temporal?**
- El navegador hace un redirect HTTP, no puede recibir JSON
- El código temporal es de uso único (se elimina al usarse)
- TTL de 30 segundos previene ataques de replay

---

### 3. TokenService.java

**Ubicación**: `src/main/java/com/example/keycloak/service/TokenService.java`

**Responsabilidades**:
- CRUD de códigos temporales en Redis
- CRUD de refresh tokens en Redis
- Gestión de TTLs

**Operaciones principales**:

```java
// Crear código temporal (TTL 30s)
public String createTempCode(TokenData tokenData) {
    String code = UUID.randomUUID().toString();
    redisTemplate.opsForValue().set(
        TEMP_CODE_PREFIX + code,
        tokenData,
        TEMP_CODE_TTL_SECONDS,
        TimeUnit.SECONDS
    );
    return code;
}

// Intercambiar código temporal (uso único)
public Optional<TokenData> exchangeTempCode(String code) {
    String key = TEMP_CODE_PREFIX + code;
    TokenData data = (TokenData) redisTemplate.opsForValue().get(key);
    if (data != null) {
        redisTemplate.delete(key);  // Eliminar después de usar
        return Optional.of(data);
    }
    return Optional.empty();
}

// Almacenar refresh token (TTL 8 horas)
public void storeRefreshToken(String userId, String refreshToken) {
    stringRedisTemplate.opsForValue().set(
        REFRESH_TOKEN_PREFIX + userId,
        refreshToken,
        REFRESH_TOKEN_TTL_HOURS,
        TimeUnit.HOURS
    );
}
```

**Estructura en Redis**:
- `temp_code:{uuid}` → TokenData (JSON) - TTL 30s
- `refresh_token:{userId}` → String (JWT) - TTL 8h

---

### 4. KeycloakTokenService.java

**Ubicación**: `src/main/java/com/example/keycloak/service/KeycloakTokenService.java`

**Responsabilidades**:
- Comunicación REST con Keycloak
- Refresh de access tokens
- Revocación de tokens en logout

```java
// Refresh token con Keycloak
public Optional<TokenResponse> refreshAccessToken(String refreshToken) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("refresh_token", refreshToken);

    ResponseEntity<Map> response = restTemplate.postForEntity(
        tokenUri,
        new HttpEntity<>(body, headers),
        Map.class
    );
    // Parsear response y devolver nuevo access token
}

// Revocar token en Keycloak
public boolean revokeToken(String refreshToken) {
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("token", refreshToken);
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);

    restTemplate.postForEntity(revokeUri, new HttpEntity<>(body, headers), Void.class);
}
```

---

### 5. AuthController.java

**Ubicación**: `src/main/java/com/example/keycloak/controller/AuthController.java`

**Endpoints**:

#### GET `/api/auth/login` (Público)
Inicia el flujo OAuth2 con Keycloak.

```java
@GetMapping("/login")
public void login(HttpServletResponse response) throws IOException {
    response.sendRedirect("/oauth2/authorization/keycloak");
}
```

#### POST `/api/auth/exchange` (Público)
Intercambia código temporal por accessToken.

```java
@PostMapping("/exchange")
public ResponseEntity<?> exchangeCode(@RequestBody ExchangeRequest request) {
    // 1. Buscar código en Redis
    Optional<TokenData> tokenDataOpt = tokenService.exchangeTempCode(code);

    // 2. Almacenar refresh token en Redis
    tokenService.storeRefreshToken(tokenData.getUserId(), tokenData.getRefreshToken());

    // 3. Devolver solo accessToken (refresh NUNCA sale del backend)
    return ResponseEntity.ok(new TokenResponse(
        tokenData.getAccessToken(),
        tokenData.getExpiresIn()
    ));
}
```

#### POST `/api/auth/refresh` (Requiere Bearer token)
Renueva el accessToken usando refresh token de Redis.

```java
@PostMapping("/refresh")
public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
    // 1. Extraer userId del token (validar firma, ignorar expiración)
    String userId = extractUserIdFromToken(expiredToken);

    // 2. Buscar refresh token en Redis
    Optional<String> refreshTokenOpt = tokenService.getRefreshToken(userId);

    // 3. Llamar a Keycloak para refrescar
    Optional<TokenResponse> newTokenOpt = keycloakTokenService.refreshAccessToken(refreshToken);

    // 4. Devolver nuevo accessToken
    return ResponseEntity.ok(newToken);
}
```

#### GET `/api/auth/status` (Requiere Bearer token)
Verifica si el token es válido.

```java
@GetMapping("/status")
public ResponseEntity<AuthStatusResponse> getAuthStatus() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthStatusResponse(true, jwt.getClaimAsString("preferred_username")));
    }

    return ResponseEntity.ok(new AuthStatusResponse(false, null, "No autenticado"));
}
```

#### POST `/api/auth/logout` (Requiere Bearer token)
Cierra sesión, revoca tokens.

```java
@PostMapping("/logout")
public ResponseEntity<LogoutResponse> logout(@RequestHeader("Authorization") String authHeader) {
    // 1. Extraer userId del token
    String userId = extractUserIdFromToken(token);

    // 2. Revocar en Keycloak
    keycloakTokenService.revokeToken(refreshToken);

    // 3. Eliminar de Redis
    tokenService.deleteRefreshToken(userId);

    return ResponseEntity.ok(new LogoutResponse(true, "Logout exitoso"));
}
```

---

## Configuración

### application.yml

```yaml
server:
  port: 8081

spring:
  data:
    redis:
      host: localhost
      port: 6379

  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: spring-boot-client
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: openid, profile, email
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          keycloak:
            issuer-uri: http://localhost:9090/realms/mi-realm
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/mi-realm

app:
  frontend:
    url: http://localhost:4200
  keycloak:
    revoke-uri: http://localhost:9090/realms/mi-realm/protocol/openid-connect/revoke
```

---

## Flujo de Autenticación

### Paso 1: Usuario inicia login

```
Angular                    Spring Boot                Keycloak
  │                            │                         │
  │ GET /api/auth/login        │                         │
  ├───────────────────────────>│                         │
  │                            │                         │
  │ 302 → /oauth2/auth/kc      │                         │
  │<───────────────────────────┤                         │
  │                            │                         │
  │                            │ 302 → KC login page     │
  │<─────────────────────────────────────────────────────┤
```

### Paso 2: Usuario se autentica en Keycloak

```
  │ POST credentials           │                         │
  ├─────────────────────────────────────────────────────>│
  │                            │                         │
  │                            │ 302 → /login/oauth2/code│
  │                            │ ?code=xxx               │
  │<─────────────────────────────────────────────────────┤
```

### Paso 3: Spring Boot intercambia código por tokens

```
  │                            │ POST /token             │
  │                            │ code + client_secret    │
  │                            ├────────────────────────>│
  │                            │                         │
  │                            │ { access_token,         │
  │                            │   refresh_token }       │
  │                            │<────────────────────────┤
```

### Paso 4: Handler genera código temporal

```
  │                            │                         │
  │                     TokenService.createTempCode()    │
  │                     → Redis: temp_code:{uuid}        │
  │                            │                         │
  │ 302 → /callback?code=uuid  │                         │
  │<───────────────────────────┤                         │
```

### Paso 5: Frontend intercambia código por accessToken

```
  │ POST /api/auth/exchange    │                         │
  │ { code: uuid }             │                         │
  ├───────────────────────────>│                         │
  │                            │                         │
  │                     Redis: GET + DELETE temp_code    │
  │                     Redis: SET refresh_token:{uid}   │
  │                            │                         │
  │ { accessToken, expiresIn } │                         │
  │<───────────────────────────┤                         │
```

### Paso 6: Peticiones con Bearer token

```
  │ GET /api/user/me           │                         │
  │ Authorization: Bearer JWT  │                         │
  ├───────────────────────────>│                         │
  │                            │                         │
  │              SecurityFilterChain                     │
  │              • Valida JWT con Keycloak JWKS          │
  │              • Extrae roles                          │
  │              • Verifica @PreAuthorize                │
  │                            │                         │
  │ 200 { username, email... } │                         │
  │<───────────────────────────┤                         │
```

---

## Redis Storage

### Estructura de Claves

| Prefijo | Contenido | TTL | Descripción |
|---------|-----------|-----|-------------|
| `temp_code:{uuid}` | TokenData (JSON) | 30s | Código temporal para exchange |
| `refresh_token:{userId}` | JWT String | 8h | Refresh token del usuario |

### Comandos Útiles

```bash
# Conectar a Redis
docker exec -it redis redis-cli

# Ver todas las claves
KEYS *

# Ver refresh tokens
KEYS refresh_token:*

# Ver contenido de una clave
GET "refresh_token:{userId}"

# Ver TTL restante
TTL "refresh_token:{userId}"

# Eliminar una clave
DEL "refresh_token:{userId}"
```

### TokenData (JSON en Redis)

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "userId": "e683f747-9c05-4d0d-b057-1f5b6a2d314e",
  "expiresIn": 300
}
```

---

## Testing

### Test 1: Endpoint Público

```bash
curl http://localhost:8081/public/status
```

**Resultado esperado**:
```json
{
  "status": "UP",
  "message": "El servidor está funcionando correctamente"
}
```

### Test 2: Exchange de Código

```bash
# Después del login, obtener código de la URL del callback
curl -X POST http://localhost:8081/api/auth/exchange \
  -H "Content-Type: application/json" \
  -d '{"code": "uuid-del-callback"}'
```

**Resultado esperado**:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "expiresIn": 300
}
```

### Test 3: Endpoint Protegido con Bearer

```bash
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer eyJhbGc..."
```

**Resultado esperado**:
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "name": "Test User",
  "roles": ["ROLE_USER", "ROLE_ADMIN"]
}
```

### Test 4: Refresh Token

```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H "Authorization: Bearer eyJhbGc..."
```

**Resultado esperado**:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "expiresIn": 300
}
```

### Test 5: Logout

```bash
curl -X POST http://localhost:8081/api/auth/logout \
  -H "Authorization: Bearer eyJhbGc..."
```

**Resultado esperado**:
```json
{
  "success": true,
  "message": "Logout exitoso"
}
```

---

## Troubleshooting

### Error: "Código temporal inválido o expirado"

**Causa**: El código temporal tiene TTL de 30 segundos.

**Solución**:
1. Verificar que Redis esté corriendo: `docker-compose ps`
2. El exchange debe hacerse rápidamente después del redirect
3. Revisar logs del backend para más detalles

### Error: "No se encontró refresh token"

**Causa**: El usuario fue deslogueado o sesión expiró.

**Solución**:
1. Nuevo login invalida el refresh token anterior
2. Verificar en Redis: `KEYS refresh_token:*`
3. Hacer login nuevamente

### Error: 401 en todas las peticiones

**Causa**: Token inválido o no se está enviando.

**Diagnóstico**:
1. Verificar que el token esté en localStorage (DevTools → Application)
2. Verificar Network tab: header `Authorization: Bearer` debe estar presente
3. Verificar logs del backend para errores de validación JWT

### Error: Redis connection refused

**Causa**: Redis no está corriendo.

**Solución**:
```bash
docker-compose up -d redis
docker-compose ps
```

---

## Comparación: Headers vs Cookies

| Aspecto | Headers + Redis (esta rama) | Cookies HttpOnly |
|---------|----------------------------|------------------|
| **XSS** | Expuesto (localStorage) | Protegido |
| **CSRF** | No aplica | Posible (mitigado con SameSite) |
| **Cross-domain** | Simple | Complejo |
| **API Gateway** | Compatible | Problemático |
| **Escalabilidad** | Redis (horizontal) | Sesiones server-side |
| **Refresh Token** | Seguro en Redis | Seguro en cookie |
| **Sesiones** | STATELESS | STATEFUL |

---

## Referencias

- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Spring Data Redis](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)

---

**Ir a**: [README](README.md) | [Frontend](FRONT_BFF.md) | [Mejoras](MEJORAS.md)

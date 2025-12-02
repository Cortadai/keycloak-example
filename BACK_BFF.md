# Backend - Spring Boot BFF con Binding (JWT + Cookie HttpOnly)

Documentacion completa del backend implementado con Spring Boot, Spring Security OAuth2 y el patron "Llave Partida".

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Patron Llave Partida](#patron-llave-partida)
- [Componentes Principales](#componentes-principales)
- [Configuracion](#configuracion)
- [Flujo de Autenticacion](#flujo-de-autenticacion)
- [Redis Storage](#redis-storage)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura

### Patron BFF con Binding

El backend implementa el patron BFF (Backend for Frontend) con **Binding** donde:

1. **Actua como OAuth2 Client** hacia Keycloak
2. **Genera JWT propio** con claim `fingerprint` (no usa directamente el de Keycloak)
3. **Gestiona Cookie HttpOnly** con hash SHA-256 del fingerprint
4. **Valida Binding** en cada peticion (JWT + Cookie deben coincidir)
5. **Almacena refresh tokens en Redis** (nunca expuestos al frontend)
6. **Sesiones STATELESS** - no usa sesiones HTTP

### Diagrama de Componentes

```
+---------------------------------------------------------------+
|                Spring Boot Application                         |
|                                                               |
|  +----------------------------------------------------------+ |
|  |              SecurityConfig (BFF + Binding)               | |
|  |  * CORS con credentials (para cookies)                    | |
|  |  * STATELESS sessions                                     | |
|  |  * OAuth2 Login + Resource Server                         | |
|  |  * Custom JwtDecoder (JWT propio, no Keycloak)            | |
|  |  * FingerprintValidationFilter                            | |
|  +----------------------------------------------------------+ |
|                                                               |
|  +----------------------------------------------------------+ |
|  |           FingerprintValidationFilter                     | |
|  |  * Ejecuta ANTES del filtro JWT                           | |
|  |  * Extrae fingerprint del JWT                             | |
|  |  * Extrae hash de la Cookie HttpOnly                      | |
|  |  * Valida: SHA-256(fingerprint) == cookie                 | |
|  |  * Si no coincide: 401 Unauthorized                       | |
|  +----------------------------------------------------------+ |
|                                                               |
|  +------------------+    +-----------------------------+     |
|  | FingerprintService|    |        JwtService           |     |
|  | * UUID.randomUUID |    | * Genera JWT propio         |     |
|  | * SHA-256 hash    |    | * Firma con HMAC            |     |
|  | * validateBinding |    | * Incluye claim fingerprint |     |
|  +------------------+    +-----------------------------+     |
|                                                               |
|  +------------------+    +-----------------------------+     |
|  |  TokenService    |    |   KeycloakTokenService      |     |
|  |  * CRUD Redis    |    |   * Refresh tokens          |     |
|  |  * Temp codes    |    |   * Revoke tokens           |     |
|  |  * Refresh tokens|    |   * Comunicacion KC         |     |
|  +------------------+    +-----------------------------+     |
|                                                               |
|  +----------------------------------------------------------+ |
|  |                      Controllers                          | |
|  |  * AuthController (/exchange, /refresh, /logout)          | |
|  |  * UserController (@PreAuthorize con roles)               | |
|  |  * AdminController (@PreAuthorize)                        | |
|  |  * PublicController                                       | |
|  +----------------------------------------------------------+ |
+---------------------------------------------------------------+
                              |
                              v
                    +-----------------+
                    |     Redis       |
                    | * temp_code:*   |
                    | * refresh:*     |
                    +-----------------+
```

---

## Patron Llave Partida

### Concepto

El binding requiere **AMBOS** elementos para autenticarse:

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

### Por que JWT propio (no Keycloak directamente)?

1. **Necesitamos el claim `fingerprint`** - Keycloak no lo tiene
2. **No podemos modificar un JWT firmado** - Romperia la firma
3. **Solucion**: Crear JWT nuevo firmado con nuestro secret

---

## Componentes Principales

### 1. FingerprintService.java

**Ubicacion**: `src/main/java/com/example/keycloak/service/FingerprintService.java`

**Responsabilidades**:
- Generar fingerprint aleatorio (UUID)
- Calcular hash SHA-256
- Validar binding

```java
@Service
public class FingerprintService {

    // Genera fingerprint aleatorio (UUID criptograficamente seguro)
    public String generateFingerprint() {
        return UUID.randomUUID().toString();
    }

    // Calcula hash SHA-256 del fingerprint
    public String hashFingerprint(String fingerprint) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }

    // Valida que hash(fingerprint) == expectedHash
    public boolean validateFingerprint(String fingerprint, String expectedHash) {
        String calculatedHash = hashFingerprint(fingerprint);
        return calculatedHash.equals(expectedHash);
    }
}
```

---

### 2. JwtService.java

**Ubicacion**: `src/main/java/com/example/keycloak/service/JwtService.java`

**Responsabilidades**:
- Generar JWT propio con fingerprint
- Firmar con HMAC-SHA
- Extraer claims (incluso de tokens expirados para refresh)

```java
@Service
public class JwtService {

    // Genera JWT con claims del usuario + fingerprint
    public String generateToken(String userId, String username, String email,
                                 String name, List<String> roles, String fingerprint) {
        return Jwts.builder()
                .subject(userId)
                .claim("preferred_username", username)
                .claim("email", email)
                .claim("name", name)
                .claim("roles", roles)
                .claim("fingerprint", fingerprint)  // <-- Clave del binding
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // Extrae fingerprint del JWT
    public String extractFingerprint(String token) {
        Claims claims = parseClaimsAllowExpired(token);
        return claims.get("fingerprint", String.class);
    }

    // Permite parsear tokens expirados (necesario para refresh)
    private Claims parseClaimsAllowExpired(String token) {
        try {
            return Jwts.parser().verifyWith(signingKey)
                    .build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();  // Firma valida, solo expirado
        }
    }
}
```

---

### 3. FingerprintValidationFilter.java

**Ubicacion**: `src/main/java/com/example/keycloak/filter/FingerprintValidationFilter.java`

**Responsabilidades**:
- Ejecutarse ANTES del filtro JWT de Spring Security
- Validar binding en cada peticion protegida
- Rechazar si binding invalido

```java
@Component
public class FingerprintValidationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {

        // Saltar endpoints publicos
        if (shouldSkipValidation(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extraer fingerprint del JWT
        String token = extractBearerToken(request);
        String fingerprint = jwtService.extractFingerprint(token);

        // Extraer hash de la cookie
        String cookieHash = extractCookieValue(request, "Fingerprint");

        // Validar binding
        if (!fingerprintService.validateFingerprint(fingerprint, cookieHash)) {
            // BINDING INVALIDO - posible token robado
            response.setStatus(401);
            response.getWriter().write("{\"message\":\"Binding invalido\"}");
            return;
        }

        // Binding valido, continuar
        filterChain.doFilter(request, response);
    }
}
```

---

### 4. AuthController.java

**Ubicacion**: `src/main/java/com/example/keycloak/controller/AuthController.java`

**Endpoints principales**:

#### POST `/api/auth/exchange`

Intercambia codigo temporal por JWT + Cookie.

```java
@PostMapping("/exchange")
public ResponseEntity<?> exchangeCode(@RequestBody ExchangeRequest request,
                                       HttpServletResponse response) {
    // 1. Obtener TokenData de Redis
    TokenData tokenData = tokenService.exchangeTempCode(code);

    // 2. Generar fingerprint
    String fingerprint = fingerprintService.generateFingerprint();
    String fingerprintHash = fingerprintService.hashFingerprint(fingerprint);

    // 3. Crear JWT propio CON fingerprint
    String jwt = jwtService.generateToken(
        tokenData.getUserId(),
        tokenData.getUsername(),
        tokenData.getEmail(),
        tokenData.getName(),
        tokenData.getRoles(),
        fingerprint  // <-- Incluido en JWT
    );

    // 4. Setear Cookie HttpOnly con hash
    Cookie cookie = new Cookie("Fingerprint", fingerprintHash);
    cookie.setHttpOnly(true);
    cookie.setSecure(false);  // true en produccion
    cookie.setSameSite("Strict");
    response.addCookie(cookie);

    // 5. Almacenar refresh token en Redis
    tokenService.storeRefreshToken(userId, tokenData.getRefreshToken());

    // 6. Devolver JWT en body (para localStorage)
    return ResponseEntity.ok(new TokenResponse(jwt, expiresIn));
}
```

#### POST `/api/auth/refresh`

Renueva JWT y **rota el fingerprint** (seguridad adicional).

```java
@PostMapping("/refresh")
public ResponseEntity<?> refreshToken(HttpServletRequest request,
                                       HttpServletResponse response) {
    // 1. Extraer JWT actual (puede estar expirado)
    String expiredToken = extractBearerToken(request);
    Map<String, Object> claims = jwtService.extractAllClaims(expiredToken);

    // 2. Obtener refresh token de Redis
    String refreshToken = tokenService.getRefreshToken(userId);

    // 3. Refrescar con Keycloak
    TokenResponse kcResponse = keycloakTokenService.refreshAccessToken(refreshToken);

    // 4. ROTAR fingerprint (nuevo para cada refresh)
    String newFingerprint = fingerprintService.generateFingerprint();
    String newHash = fingerprintService.hashFingerprint(newFingerprint);

    // 5. Generar nuevo JWT con nuevo fingerprint
    String newJwt = jwtService.generateToken(
        claims.get("userId"),
        claims.get("username"),
        claims.get("email"),
        claims.get("name"),
        claims.get("roles"),
        newFingerprint  // <-- Nuevo fingerprint
    );

    // 6. Actualizar cookie con nuevo hash
    response.addCookie(createFingerprintCookie(newHash));

    return ResponseEntity.ok(new TokenResponse(newJwt, expiresIn));
}
```

#### POST `/api/auth/logout`

Cierra sesion y elimina cookie.

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletResponse response) {
    // 1. Revocar en Keycloak
    keycloakTokenService.revokeToken(refreshToken);

    // 2. Eliminar de Redis
    tokenService.deleteRefreshToken(userId);

    // 3. Eliminar cookie (Max-Age = 0)
    Cookie cookie = new Cookie("Fingerprint", "");
    cookie.setMaxAge(0);
    response.addCookie(cookie);

    return ResponseEntity.ok(new LogoutResponse(true));
}
```

---

### 5. SecurityConfig.java

**Ubicacion**: `src/main/java/com/example/keycloak/config/SecurityConfig.java`

**Configuracion clave**:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CORS con credentials (para cookies)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // Filtro de binding ANTES del JWT
        .addFilterBefore(fingerprintValidationFilter, UsernamePasswordAuthenticationFilter.class)

        // Resource Server con decoder personalizado (JWT propio)
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .decoder(customJwtDecoder())  // Valida JWT propio
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        )

        // STATELESS
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )

        .csrf(csrf -> csrf.disable());

    return http.build();
}

// CORS debe permitir credentials para cookies
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:4200"));
    config.setAllowCredentials(true);  // <-- Importante para cookies
    // ...
}
```

---

## Configuracion

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
        provider:
          keycloak:
            issuer-uri: http://localhost:9090/realms/mi-realm

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

## Flujo de Autenticacion

### Paso 1-4: OAuth2 con Keycloak (igual que antes)

```
Angular -> Backend -> Keycloak -> Backend
```

### Paso 5: Backend genera binding

```
Backend:
  1. Extrae claims del token de Keycloak (userId, username, email, roles)
  2. Genera fingerprint = UUID.randomUUID()
  3. Calcula fingerprintHash = SHA-256(fingerprint)
  4. Crea JWT propio con claim "fingerprint": fingerprint
  5. Setea Cookie "Fingerprint": fingerprintHash (HttpOnly)
  6. Devuelve JWT en body
```

### Paso 6: Frontend almacena

```
Frontend:
  1. Recibe JWT en response body -> localStorage
  2. Cookie se guarda automaticamente (navegador)
```

### Paso 7: Peticiones protegidas

```
Frontend:
  1. Anade header: Authorization: Bearer {jwt}
  2. withCredentials: true (cookie viaja automatica)

Backend (FingerprintValidationFilter):
  1. Extrae fingerprint del JWT
  2. Extrae hash de Cookie
  3. Valida: SHA-256(fingerprint) == hash?
  4. Si valido: continua a Spring Security
  5. Si invalido: 401 Unauthorized
```

---

## Redis Storage

### Estructura de Claves

| Prefijo | Contenido | TTL | Descripcion |
|---------|-----------|-----|-------------|
| `temp_code:{uuid}` | TokenData (JSON) | 30s | Codigo temporal para exchange |
| `refresh_token:{userId}` | JWT String | 8h | Refresh token de Keycloak |

### Comandos Utiles

```bash
# Conectar a Redis
docker exec -it redis redis-cli

# Ver todas las claves
KEYS *

# Ver refresh tokens
KEYS refresh_token:*

# Ver contenido
GET "refresh_token:{userId}"

# Ver TTL
TTL "refresh_token:{userId}"
```

---

## Testing

### Test 1: Verificar Binding

```bash
# SIN cookie (debe fallar)
curl -X GET http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {jwt}"
# Resultado: 401 - "Binding requerido - cookie faltante"

# CON cookie (debe funcionar)
curl -X GET http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {jwt}" \
  -H "Cookie: Fingerprint={hash}"
# Resultado: 200 - User data
```

### Test 2: Verificar en DevTools

1. Login en la aplicacion
2. Abrir DevTools -> Application
3. **Local Storage**: verificar `access_token`
4. **Cookies**: verificar `Fingerprint` (HttpOnly = no visible en JS)

### Test 3: Simular Ataque XSS

1. Copiar JWT de localStorage
2. Intentar usar desde otra maquina (sin cookie)
3. Resultado: 401 - Binding invalido

---

## Troubleshooting

### Error: "Binding requerido - cookie faltante"

**Causa**: Cookie no viaja en la peticion.

**Solucion**:
1. Verificar `withCredentials: true` en interceptor Angular
2. Verificar CORS `allowCredentials: true` en backend
3. Verificar que origen es exacto (no wildcard)

### Error: "Binding invalido"

**Causa**: El hash no coincide.

**Posibles razones**:
- Token robado intentando usar sin cookie
- Fingerprint rotado pero usando JWT anterior
- Manipulacion del JWT

**Solucion**: Hacer login nuevamente

### Error: Cookie no aparece en DevTools

**Causa**: Cookie HttpOnly no es visible en JavaScript.

**Solucion**: Es correcto! HttpOnly significa que JS no puede acceder. Verificar en Network tab que la cookie viaja en las peticiones.

---

## Comparacion: Binding vs Solo Headers

| Aspecto | Solo Headers | Binding (JWT + Cookie) |
|---------|--------------|------------------------|
| XSS | Expuesto | Protegido |
| CSRF | Protegido | Protegido |
| Complejidad | Baja | Media |
| Cross-domain | Facil | Requiere configuracion |

---

**Ir a**: [README](README.md) | [Frontend](FRONT_BFF.md) | [Mejoras](MEJORAS.md)

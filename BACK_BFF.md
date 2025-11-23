# Backend - Spring Boot BFF

Documentación completa del backend implementado con Spring Boot y Spring Security OAuth2.

---

## 📋 Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Componentes Principales](#componentes-principales)
- [Configuración](#configuración)
- [Flujo de Autenticación](#flujo-de-autenticación)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## 🏗️ Arquitectura

### Patrón BFF en el Backend

El backend implementa el patrón BFF (Backend for Frontend) donde:

1. **Actúa como OAuth2 Client** hacia Keycloak
2. **Actúa como Resource Server** para validar JWTs
3. **Gestiona cookies HttpOnly** con los tokens
4. **Valida roles** extraídos de Keycloak
5. **Protege endpoints** con Spring Security

### Diagrama de Componentes

```
┌────────────────────────────────────────────┐
│         Spring Boot Application            │
│                                            │
│  ┌──────────────────────────────────────┐ │
│  │      SecurityConfig (BFF)            │ │
│  │  • CORS                              │ │
│  │  • STATEFUL sessions                 │ │
│  │  • OAuth2 Login                      │ │
│  │  • Resource Server (JWT)             │ │
│  └──────────┬───────────────────────────┘ │
│             │                              │
│  ┌──────────▼───────────┐                 │
│  │ OAuth2LoginSuccess   │                 │
│  │ Handler              │                 │
│  │ • Crea cookie        │                 │
│  │   HttpOnly           │                 │
│  └──────────────────────┘                 │
│                                            │
│  ┌──────────────────────┐                 │
│  │ JwtCookieFilter      │                 │
│  │ • Extrae JWT de      │                 │
│  │   cookie             │                 │
│  │ • Convierte a header │                 │
│  └──────────────────────┘                 │
│                                            │
│  ┌──────────────────────────────────────┐ │
│  │         Controllers                  │ │
│  │  • AuthController                    │ │
│  │  • UserController (@PreAuthorize)    │ │
│  │  • AdminController (@PreAuthorize)   │ │
│  │  • PublicController                  │ │
│  └──────────────────────────────────────┘ │
└────────────────────────────────────────────┘
```

---

## 🔧 Componentes Principales

### 1. SecurityConfig.java

**Ubicación**: `src/main/java/com/example/keycloak/config/SecurityConfig.java`

**Responsabilidades**:
- Configuración BFF con sessions STATEFUL
- CORS configurado para Angular
- Integración OAuth2 Login + Resource Server
- Extracción de roles de Keycloak

**Configuración clave**:

```java
// Session STATEFUL para cookies
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
)

// CORS con credentials
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
configuration.setAllowCredentials(true);

// OAuth2 Login con handler personalizado
.oauth2Login(oauth2 -> oauth2
    .successHandler(oauth2LoginSuccessHandler)
)

// Resource Server para validar JWT
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt
        .jwtAuthenticationConverter(jwtAuthenticationConverter())
    )
)

// Filtro personalizado para cookies
.addFilterBefore(jwtCookieFilter, UsernamePasswordAuthenticationFilter.class)
```

**Extracción de roles**:
- **Realm roles**: `realm_access.roles` → `ROLE_USER`, `ROLE_ADMIN`
- **Client roles**: `resource_access.{client}.roles` → `ROLE_*`
- Se combinan todos los roles y scopes

---

### 2. OAuth2LoginSuccessHandler.java

**Ubicación**: `src/main/java/com/example/keycloak/config/OAuth2LoginSuccessHandler.java`

**Responsabilidades**:
- Captura el token JWT después del login exitoso
- Crea cookie HttpOnly con el token
- Configura atributos de seguridad (SameSite, Secure, Path)
- Redirige a Angular

**Configuración de la cookie**:

```java
private Cookie createSecureCookie(String name, String value) {
    Cookie cookie = new Cookie(name, value);
    cookie.setHttpOnly(true);              // No accesible desde JS
    cookie.setSecure(secureCookie);        // Solo HTTPS en prod
    cookie.setPath("/");                   // Disponible en toda la app
    cookie.setMaxAge(cookieMaxAge);        // Duración (1 hora default)
    cookie.setAttribute("SameSite", "Strict"); // Protección CSRF
    return cookie;
}
```

**¿Por qué SameSite=Strict?**
- Evita que la cookie se envíe en peticiones cross-site
- Protección automática contra CSRF
- Solo se envía en peticiones same-origin

---

### 3. JwtCookieFilter.java

**Ubicación**: `src/main/java/com/example/keycloak/filter/JwtCookieFilter.java`

**Responsabilidades**:
- Intercepta todas las peticiones HTTP
- Busca la cookie `ACCESS_TOKEN`
- Si existe, extrae el JWT y lo convierte en header `Authorization: Bearer {jwt}`
- Si no existe, deja pasar la petición sin modificar

**Flujo del filtro**:

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) {
    // Si ya hay header Authorization, no hacer nada
    if (request.getHeader(AUTH_HEADER) != null) {
        filterChain.doFilter(request, response);
        return;
    }

    // Buscar JWT en cookie
    String jwtFromCookie = extractJwtFromCookie(request);

    if (jwtFromCookie != null) {
        // Crear request wrapper con header Authorization
        HttpServletRequest wrappedRequest = new JwtHeaderRequestWrapper(request, jwtFromCookie);
        filterChain.doFilter(wrappedRequest, response);
    } else {
        // No hay cookie, continuar sin token
        filterChain.doFilter(request, response);
    }
}
```

**¿Por qué este filtro?**
- Spring Security OAuth2 Resource Server espera JWT en header `Authorization`
- Cookies no se procesan automáticamente
- Este filtro hace la conversión: Cookie → Header

---

### 4. AuthController.java

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

#### GET `/api/auth/status` (Público)
Verifica si hay sesión activa. Usado por Angular para check inicial.

```java
@GetMapping("/status")
public ResponseEntity<AuthStatus> checkAuthStatus(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
        return ResponseEntity.ok(new AuthStatus(true, authentication.getName()));
    }
    return ResponseEntity.ok(new AuthStatus(false, null));
}
```

#### POST `/api/auth/logout` (Autenticado)
Cierra la sesión local (Spring Boot) e invalida la cookie.

```java
@PostMapping("/logout")
public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                    HttpServletResponse response) {
    // Limpiar sesión de Spring Security
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    // Invalidar cookie
    Cookie cookie = new Cookie("ACCESS_TOKEN", null);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setMaxAge(0);  // Expira inmediatamente
    response.addCookie(cookie);

    return ResponseEntity.ok(Map.of("message", "Logout exitoso"));
}
```

**Nota sobre SSO**: Este logout es local. La sesión de Keycloak sigue activa. Para logout global SSO, ver sección "Mejoras Futuras".

---

### 5. UserController.java

**Ubicación**: `src/main/java/com/example/keycloak/controller/UserController.java`

Endpoints protegidos con rol `USER`:

#### GET `/api/user/me`
Información básica del usuario autenticado.

```java
@GetMapping("/me")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<UserInfo> getCurrentUser(Authentication authentication) {
    // Extrae username, email, roles del JWT
    return ResponseEntity.ok(userInfo);
}
```

#### GET `/api/user/profile`
Perfil completo del usuario.

#### GET `/api/user/dashboard`
Datos del dashboard del usuario.

#### GET `/api/user/token-info`
Claims del JWT (útil para debugging).

---

### 6. AdminController.java

**Ubicación**: `src/main/java/com/example/keycloak/controller/AdminController.java`

Endpoints protegidos con rol `ADMIN`:

```java
@GetMapping("/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Map<String, Object>> getAdminDashboard() {
    // Solo accesible para usuarios con rol ADMIN
}
```

---

## ⚙️ Configuración

### application.yml

**Ubicación**: `src/main/resources/application.yml`

```yaml
server:
  port: 8081
  servlet:
    session:
      cookie:
        same-site: strict
        http-only: true
        secure: false  # true en producción

spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: spring-boot-client
            client-secret: {tu-secret}
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
  cookie:
    secure: false
    max-age: 3600
```

**Variables importantes**:
- `client-secret`: Obtener de Keycloak (Client → Credentials)
- `issuer-uri`: URL del realm de Keycloak
- `app.frontend.url`: URL de Angular para redirect post-login
- `app.cookie.secure`: `false` en dev, `true` en prod (requiere HTTPS)

---

## 🔄 Flujo de Autenticación Detallado

### Paso 1: Usuario hace login

```
Angular                    Spring Boot                Keycloak
  │                            │                         │
  │ GET /api/auth/login        │                         │
  ├───────────────────────────>│                         │
  │                            │                         │
  │ 302 Redirect               │                         │
  │ /oauth2/authorization/kc   │                         │
  │<───────────────────────────┤                         │
  │                            │                         │
  │                            │ 302 Redirect            │
  │                            │ /auth?response_type=code│
  │                            ├────────────────────────>│
  │                            │                         │
  │ Mostrar login form         │                         │
  │<────────────────────────────────────────────────────┤
```

### Paso 2: Autenticación en Keycloak

```
  │ POST /auth (credentials)   │                         │
  ├─────────────────────────────────────────────────────>│
  │                            │                         │
  │                            │ 302 Redirect            │
  │                            │ /login/oauth2/code/kc   │
  │                            │ ?code=xxx               │
  │<────────────────────────────────────────────────────┤
```

### Paso 3: Spring Boot intercambia código por token

```
  │                            │ POST /token             │
  │                            │ code=xxx                │
  │                            │ client_id=...           │
  │                            │ client_secret=...       │
  │                            ├────────────────────────>│
  │                            │                         │
  │                            │ 200 OK                  │
  │                            │ { access_token: JWT }   │
  │                            │<────────────────────────┤
```

### Paso 4: OAuth2LoginSuccessHandler crea cookie

```java
@Override
public void onAuthenticationSuccess(HttpServletRequest request,
                                      HttpServletResponse response,
                                      Authentication authentication) {
    // Extraer JWT del authentication
    OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
    String jwt = extractJwtToken(oauthToken);

    // Crear cookie HttpOnly
    Cookie cookie = createSecureCookie("ACCESS_TOKEN", jwt);
    response.addCookie(cookie);

    // Redirect a Angular
    response.sendRedirect(frontendUrl);
}
```

### Paso 5: Angular recibe cookie y redirige a dashboard

```
  │ 302 Redirect               │                         │
  │ http://localhost:4200      │                         │
  │ Set-Cookie: ACCESS_TOKEN   │                         │
  │<───────────────────────────┤                         │
```

### Paso 6: Peticiones subsecuentes con cookie

```
  │ GET /api/user/me           │                         │
  │ Cookie: ACCESS_TOKEN=JWT   │                         │
  ├───────────────────────────>│                         │
  │                            │                         │
  │                 JwtCookieFilter                      │
  │                 • Extrae JWT                         │
  │                 • Añade Authorization header         │
  │                            │                         │
  │                 SecurityFilterChain                  │
  │                 • Valida JWT                         │
  │                 • Extrae roles                       │
  │                 • Verifica @PreAuthorize             │
  │                            │                         │
  │ 200 OK                     │                         │
  │ { username, email, ... }   │                         │
  │<───────────────────────────┤                         │
```

---

## 🧪 Testing

### Test 1: Endpoint Público

```bash
curl http://localhost:8081/public/status
```

**Resultado esperado**:
```json
{
  "status": "OK",
  "message": "Public endpoint accessible"
}
```

### Test 2: Flujo OAuth2 Completo

1. **Iniciar login**:
```bash
curl -v http://localhost:8081/api/auth/login
```

Deberías ver redirect 302 a Keycloak.

2. **Completar login en navegador**:
   - Abrir `http://localhost:8081/api/auth/login` en navegador
   - Autenticarse en Keycloak
   - Verificar redirect a Angular

3. **Verificar cookie en DevTools**:
   - Abrir DevTools → Application → Cookies → `http://localhost:8081`
   - Buscar `ACCESS_TOKEN`
   - Verificar `HttpOnly=true`, `SameSite=Strict`

### Test 3: Endpoint Protegido con Cookie

```bash
# Obtener la cookie del navegador (DevTools)
export JWT="eyJhbGc..."

# Petición con cookie
curl -v http://localhost:8081/api/user/me \
  -H "Cookie: ACCESS_TOKEN=$JWT"
```

**Resultado esperado**:
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "name": "Test User",
  "roles": ["USER"],
  "authenticated": true
}
```

### Test 4: Endpoint sin Cookie (401)

```bash
curl -v http://localhost:8081/api/user/me
```

**Resultado esperado**: `401 Unauthorized`

### Test 5: Endpoint con rol incorrecto (403)

Usuario con solo rol USER intentando acceder a endpoint ADMIN:

```bash
curl -v http://localhost:8081/api/admin/dashboard \
  -H "Cookie: ACCESS_TOKEN=$JWT_USER"
```

**Resultado esperado**: `403 Forbidden`

### Test 6: Logout

```bash
curl -X POST http://localhost:8081/api/auth/logout \
  -H "Cookie: ACCESS_TOKEN=$JWT"
```

**Resultado esperado**:
```json
{
  "message": "Logout exitoso",
  "redirect": "http://localhost:4200/login"
}
```

La cookie debe expirar (`Max-Age=0`).

---

## 🐛 Troubleshooting

### Problema: Cookie no tiene SameSite=Strict

**Síntoma**: En DevTools, cookie `ACCESS_TOKEN` no muestra `SameSite=Strict`.

**Causa**: Falta atributo en `OAuth2LoginSuccessHandler`.

**Solución**: Verificar línea 118 en `OAuth2LoginSuccessHandler.java`:

```java
cookie.setAttribute("SameSite", "Strict");
```

---

### Problema: Error CORS

**Síntoma**:
```
Access to fetch at 'http://localhost:8081/api/auth/status' from origin
'http://localhost:4200' has been blocked by CORS policy
```

**Causas posibles**:
1. Spring Boot no está corriendo
2. CORS no está configurado correctamente
3. `allowCredentials` no está en `true`

**Solución**: Verificar `SecurityConfig.java` línea 134-163:

```java
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
configuration.setAllowCredentials(true);
```

Reiniciar Spring Boot.

---

### Problema: 401 en todas las peticiones

**Síntoma**: Todas las peticiones desde Angular devuelven 401.

**Causas posibles**:
1. Cookie no se está creando
2. Cookie no se está enviando
3. `JwtCookieFilter` no está funcionando

**Diagnóstico**:

1. Verificar logs de Spring Boot:
```
OAuth2LoginSuccessHandler - Cookie de sesión creada exitosamente
```

2. Verificar cookie en DevTools.

3. Verificar Network tab: header `Cookie` debe estar presente.

4. Agregar logging en `JwtCookieFilter`:
```java
@Override
protected void doFilterInternal(...) {
    logger.info("Buscando JWT en cookies");
    String jwt = extractJwtFromCookie(request);
    logger.info("JWT encontrado: " + (jwt != null ? "SÍ" : "NO"));
    // ...
}
```

---

### Problema: Roles no se extraen correctamente

**Síntoma**: Usuario tiene roles en Keycloak pero `@PreAuthorize("hasRole('USER')")` falla.

**Diagnóstico**:

1. Verificar token en `/api/user/token-info`:
```bash
curl http://localhost:8081/api/user/token-info \
  -H "Cookie: ACCESS_TOKEN=$JWT"
```

2. Buscar en el response:
```json
{
  "realm_access": {
    "roles": ["user", "admin"]
  },
  "resource_access": {
    "spring-boot-client": {
      "roles": ["user"]
    }
  }
}
```

3. Verificar extracción en `SecurityConfig.java`:
   - `extractRealmRoles()` debe convertir `"user"` → `"ROLE_USER"`
   - `extractClientRoles()` debe hacer lo mismo

**Solución**: Los roles en Keycloak deben estar en minúsculas y el código los convierte a mayúsculas con prefijo `ROLE_`.

---

### Problema: Sesión expira muy rápido

**Síntoma**: Después de unos minutos, necesitas hacer login nuevamente.

**Causa**: `cookie.max-age` muy corto.

**Solución**: Ajustar en `application.yml`:

```yaml
app:
  cookie:
    max-age: 3600  # 1 hora (en segundos)
```

O en `application.yml` de session:

```yaml
server:
  servlet:
    session:
      timeout: 30m  # 30 minutos
```

---

## 🚀 Mejoras Futuras

### 1. Logout Global SSO

Actualmente, el logout solo invalida la sesión local de Spring Boot. La sesión de Keycloak sigue activa.

**Implementación**:

```java
@PostMapping("/logout")
public ResponseEntity<Map<String, String>> logout(...) {
    // 1. Logout local (actual)
    new SecurityContextLogoutHandler().logout(request, response, authentication);

    // 2. Construir URL de logout de Keycloak
    String keycloakLogoutUrl = "http://localhost:9090/realms/mi-realm/protocol/openid-connect/logout";
    String postLogoutRedirectUri = "http://localhost:4200/login";
    String idToken = extractIdToken(authentication);

    String logoutUrl = String.format("%s?id_token_hint=%s&post_logout_redirect_uri=%s",
        keycloakLogoutUrl, idToken, postLogoutRedirectUri);

    // 3. Invalidar cookie local
    Cookie cookie = new Cookie("ACCESS_TOKEN", null);
    cookie.setMaxAge(0);
    response.addCookie(cookie);

    // 4. Retornar URL para que Angular redirija
    return ResponseEntity.ok(Map.of(
        "message", "Logout exitoso",
        "logoutUrl", logoutUrl
    ));
}
```

### 2. Refresh Token Automático

Implementar endpoint para refresh transparente cuando el token expira.

### 3. CSRF Protection

Habilitar CSRF en producción con token-based approach para Angular.

---

## 📖 Referencias

- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/index.html)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)
- [Keycloak Spring Boot Adapter](https://www.keycloak.org/docs/latest/securing_apps/#_spring_boot_adapter)
- [Cookie Security Best Practices](https://owasp.org/www-community/controls/SecureCookieAttribute)

---

**Ir a**: [Documentación Principal](README.md) | [Documentación Frontend](FRONT_BFF.md)

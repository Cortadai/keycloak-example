# Keycloak Spring Boot + Angular - SPA con Authorization Code + PKCE

## 🎯 Descripción

Aplicación de demostración que implementa el patrón **SPA tradicional** con autenticación OAuth2 mediante Keycloak, utilizando **Authorization Code Flow + PKCE**.

### Tecnologías Principales

- **Backend**: Spring Boot 3.x + Spring Security (Resource Server)
- **Frontend**: Angular 21 + angular-oauth2-oidc
- **Autenticación**: Keycloak (OAuth2 Authorization Code + PKCE)
- **Token Storage**: localStorage (accesible desde JavaScript)

---

## 🏗️ Arquitectura SPA + PKCE

### ¿Qué es PKCE?

**PKCE** (Proof Key for Code Exchange) es una extensión de OAuth2 que protege el Authorization Code Flow en clientes públicos (SPAs, aplicaciones móviles).

### ¿En qué se diferencia de BFF?

| Aspecto | SPA + PKCE (esta rama) | BFF (oauth2-bff) |
|---------|------------------------|-------------------|
| **Token storage** | localStorage/sessionStorage | Cookie HttpOnly |
| **Acceso al token** | JavaScript puede leer | JavaScript NO puede leer |
| **Login** | SPA → Keycloak directamente | SPA → Backend → Keycloak |
| **Backend** | Solo Resource Server | Resource Server + OAuth2 Client |
| **Sesiones** | STATELESS | STATEFUL |
| **Seguridad XSS** | ⚠️ Vulnerable | ✅ Protegido |
| **Complejidad** | 🟢 Simple | 🟡 Moderada |
| **Uso típico** | APIs públicas, low-risk | Apps empresariales, high-security |

### Flujo de Autenticación

```
┌─────────┐         ┌──────────────┐         ┌──────────┐
│ Angular │         │ Spring Boot  │         │ Keycloak │
│  :4200  │         │    :8081     │         │  :9090   │
└────┬────┘         └──────┬───────┘         └────┬─────┘
     │                     │                      │
     │ 1. Click "Login"    │                      │
     │ 2. Generate PKCE    │                      │
     │    code_verifier & code_challenge          │
     │                     │                      │
     │ 3. Redirect to Keycloak with code_challenge
     ├─────────────────────┼─────────────────────>│
     │                     │                      │
     │ 4. Login form       │                      │
     │<────────────────────┼──────────────────────┤
     │                     │                      │
     │ 5. Submit credentials                      │
     ├─────────────────────┼─────────────────────>│
     │                     │                      │
     │ 6. Authorization code                      │
     │<────────────────────┼──────────────────────┤
     │                     │                      │
     │ 7. Exchange code + code_verifier for tokens
     ├─────────────────────┼─────────────────────>│
     │                     │                      │
     │ 8. JWT tokens (access + refresh)           │
     │<────────────────────┼──────────────────────┤
     │                     │                      │
     │ 9. Store in localStorage                   │
     │                     │                      │
     │ 10. GET /api/user/me                       │
     │     Header: Authorization: Bearer {token}  │
     ├────────────────────>│                      │
     │                     │ 11. Validate JWT     │
     │                     │ 12. Extract roles    │
     │<────────────────────┤                      │
     │ 13. User data       │                      │
```

### Características de Seguridad

✅ **PKCE**: Protege contra interceptación del authorization code
✅ **CORS configurado**: Solo origins específicos permitidos
✅ **Validación JWT**: Backend valida todos los tokens
✅ **Role-Based Access Control**: Roles de Keycloak extraídos automáticamente
✅ **STATELESS**: Sin sesiones en backend

⚠️ **Limitaciones de seguridad:**
- Token accesible desde JavaScript (vulnerable a XSS)
- Requiere Content Security Policy estricta
- Sanitización de inputs crítica

---

## 📁 Estructura del Proyecto

```
keycloak-spring-demo/
├── src/main/java/com/example/keycloak/
│   ├── config/
│   │   └── SecurityConfig.java              # Resource Server STATELESS
│   ├── controller/
│   │   ├── AuthController.java              # Solo /status y /info
│   │   ├── UserController.java              # Endpoints USER
│   │   ├── AdminController.java             # Endpoints ADMIN
│   │   └── PublicController.java            # Endpoints públicos
│   └── model/
│       └── UserInfo.java                    # DTOs
│
├── frontend/src/app/
│   ├── core/
│   │   ├── services/
│   │   │   └── auth.service.ts              # OAuthService + PKCE
│   │   ├── guards/
│   │   │   └── auth.guard.ts                # Guards sin HTTP calls
│   │   └── interceptors/
│   │       └── auth.interceptor.ts          # Authorization header
│   ├── app.config.ts                        # provideOAuthClient()
│   └── app.routes.ts                        # Rutas protegidas
│
└── README.md                                # Este archivo
```

---

## 🚀 Inicio Rápido

### Prerequisitos

1. **Java 17+** y Maven instalados
2. **Node.js 18+** y npm instalados
3. **Docker** para Keycloak

### Paso 1: Levantar Keycloak

```bash
docker run -d \
  --name keycloak \
  -p 9090:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

### Paso 2: Configurar Keycloak para PKCE

1. Acceder a `http://localhost:9090/admin`
2. Login con admin/admin
3. Crear realm: `mi-realm`
4. Crear client: `spring-boot-client`
   - **Client type**: `OpenID Connect`
   - **Client authentication**: `OFF` ← CRÍTICO (cliente público)
   - **Standard flow**: `ON`
   - **Direct access grants**: `OFF` (no necesario para PKCE)
   - **Valid Redirect URIs**: `http://localhost:4200/*`
   - **Web Origins**: `http://localhost:4200`
   - **Advanced Settings**:
     - **Proof Key for Code Exchange Code Challenge Method**: `S256` ← CRÍTICO
5. Crear roles: `user`, `admin`
6. Crear usuarios de prueba y asignar roles

### Paso 3: Ejecutar Backend

```bash
mvn clean install
mvn spring-boot:run
```

Verificar: `http://localhost:8081/public/hello`

### Paso 4: Instalar Dependencias Frontend

```bash
cd frontend
npm install
```

### Paso 5: Ejecutar Frontend

```bash
npm start
```

Verificar: `http://localhost:4200`

### Paso 6: Probar

1. Navegar a `http://localhost:4200`
2. Click en "Login"
3. Angular genera PKCE y redirige a Keycloak
4. Autenticarse en Keycloak
5. Keycloak redirige de vuelta a Angular
6. Angular intercambia code por tokens
7. Tokens guardados en localStorage
8. Verificar en DevTools → Application → Local Storage

---

## 🔐 Endpoints de la API

### Autenticación

| Endpoint | Método | Acceso | Descripción |
|----------|--------|--------|-------------|
| `/api/auth/status` | GET | Autenticado | Verifica token válido |
| `/api/auth/info` | GET | Público | Información del flujo PKCE |

### Usuario (Rol: user)

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/api/user/me` | GET | Info del usuario autenticado |
| `/api/user/profile` | GET | Perfil completo |
| `/api/user/dashboard` | GET | Dashboard del usuario |

### Admin (Rol: admin)

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

---

## 🔧 Configuración

### Puertos

- **Keycloak**: 9090 (Docker: 9090:8080)
- **Spring Boot**: 8081
- **Angular**: 4200

### Backend (`application.yml`)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/mi-realm
```

### Frontend (`auth.service.ts`)

```typescript
const authConfig: AuthConfig = {
  issuer: 'http://localhost:9090/realms/mi-realm',
  clientId: 'spring-boot-client',
  redirectUri: window.location.origin,
  responseType: 'code',
  scope: 'openid profile email',
  usePkce: true  // ← CRÍTICO
};
```

---

## 📊 Comparación con Otras Ramas

### Rama `main` - Resource Server Básico

**Qué es:**
- Backend Resource Server
- Sin frontend
- Tokens obtenidos con cURL

**Cuándo usar:**
- Aprender conceptos básicos
- APIs sin frontend

**Complejidad:** 🌱 Principiante

---

### Rama `oauth2-resource-server` - M2M

**Qué es:**
- Client Credentials
- Service Accounts
- M2M communication

**Cuándo usar:**
- APIs backend que se comunican entre sí
- Microservicios
- Cron jobs

**Complejidad:** 🌿 Intermedio

---

### Rama `oauth2-spa-pkce` - SPA Tradicional (ESTA RAMA)

**Qué es:**
- Authorization Code + PKCE
- JWT en localStorage
- SPA gestiona autenticación

**Cuándo usar:**
- SPAs (React, Angular, Vue)
- APIs públicas
- Low-medium security requirements
- CORS entre dominios

**Ventajas:**
- ✅ Simple de implementar
- ✅ Frontend tiene control total
- ✅ CORS fácil

**Desventajas:**
- ⚠️ Token accesible desde JavaScript
- ⚠️ Vulnerable a XSS
- ⚠️ Requiere CSP estricta

**Complejidad:** 🌳 Avanzado

---

### Rama `oauth2-bff` - BFF con Cookies

**Qué es:**
- Authorization Code (sin PKCE)
- Cookies HttpOnly
- Backend gestiona tokens

**Cuándo usar:**
- SPAs empresariales
- High security requirements
- Mismo dominio backend/frontend

**Ventajas:**
- ✅ Máxima seguridad (HttpOnly cookies)
- ✅ Token NO accesible desde JavaScript
- ✅ Protección contra XSS

**Desventajas:**
- ⚠️ Más complejo
- ⚠️ Requiere backend para login
- ⚠️ CORS con credentials

**Complejidad:** 🌲 Avanzado+

---

## 🎓 Conceptos Aprendidos

### Seguridad
- OAuth2 Authorization Code Flow
- PKCE (Proof Key for Code Exchange)
- JWT validation y RBAC
- XSS risks y mitigaciones
- Content Security Policy

### Tecnologías
- Spring Security Resource Server
- angular-oauth2-oidc
- Keycloak public clients
- Token storage strategies
- Guard-based authorization

---

## ⚠️ Seguridad en Producción

### CRÍTICO para producción:

1. **Content Security Policy**
   ```html
   <meta http-equiv="Content-Security-Policy"
         content="default-src 'self'; script-src 'self'">
   ```

2. **Sanitización de inputs**
   - Usar DomSanitizer en Angular
   - Validar todos los inputs del usuario

3. **HTTPS obligatorio**
   - Tokens solo por HTTPS
   - Secure flag en producción

4. **Token rotation**
   - Implementar refresh token automático
   - Tokens de corta duración

5. **Logging seguro**
   - NO loguear tokens
   - Monitorear intentos de acceso

---

## 🚧 Mejoras Futuras (Opcional)

1. **Refresh Token Automático**
   - Interceptor que detecta 401 y refresh transparente

2. **Silent Refresh**
   - iframe para renovar tokens sin logout

3. **Session Monitoring**
   - Detectar inactividad y hacer logout

4. **Multi-tab Sync**
   - Sincronizar estado entre pestañas

---

## ❓ Troubleshooting

### Error CORS

**Síntoma:** `Access to XMLHttpRequest has been blocked by CORS policy`

**Solución:**
- Verificar SecurityConfig.java CORS configuration
- Verificar Keycloak Web Origins: `http://localhost:4200`

### Token no se guarda en localStorage

**Síntoma:** Después del login, no hay token en localStorage

**Solución:**
- Verificar redirect URI en Keycloak: `http://localhost:4200/*`
- Verificar client authentication = OFF en Keycloak
- Ver console logs de angular-oauth2-oidc

### PKCE no funciona

**Síntoma:** Error "PKCE verification failed"

**Solución:**
- Verificar en Keycloak → Client → Advanced Settings
- **Proof Key for Code Exchange Code Challenge Method**: `S256`
- Verificar `usePkce: true` en AuthConfig

### 401 en todas las peticiones

**Síntoma:** Backend devuelve 401 aunque hay token

**Solución:**
- Verificar que interceptor añade Authorization header
- Verificar issuer-uri en application.yml
- Ver logs de Spring Security

---

## 📖 Referencias

- [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [PKCE RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636)
- [angular-oauth2-oidc Documentation](https://github.com/manfredsteyer/angular-oauth2-oidc)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/index.html)

---

## 🎯 Siguiente Paso

Según tus necesidades:

### Si necesitas más seguridad:
**Rama `oauth2-bff`** - Cookies HttpOnly, máxima protección

```bash
git checkout oauth2-bff
```

### Si necesitas M2M:
**Rama `oauth2-resource-server`** - Client Credentials, Service Accounts

```bash
git checkout oauth2-resource-server
```

### Si quieres lo básico:
**Rama `main`** - Resource Server educativo simple

```bash
git checkout main
```

---

**¿Listo para implementar?** Sigue el [Inicio Rápido](#-inicio-rápido) 🚀

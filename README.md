# Keycloak Spring Boot + Angular - Patrón BFF

## 🎯 Descripción

Aplicación de demostración que implementa el **patrón Backend for Frontend (BFF)** con autenticación OAuth2 mediante Keycloak, utilizando cookies HttpOnly para máxima seguridad.

### Tecnologías Principales

- **Backend**: Spring Boot 3.x + Spring Security OAuth2
- **Frontend**: Angular 21 (Standalone Components + Signals)
- **Autenticación**: Keycloak (OAuth2 Authorization Code Flow)
- **Seguridad**: JWT en cookies HttpOnly + SameSite=Strict

---

## 🏗️ Arquitectura BFF

### ¿Qué es el patrón BFF?

El **Backend for Frontend** es un patrón arquitectónico donde:

1. El **frontend NO almacena tokens** en localStorage/sessionStorage (vulnerable a XSS)
2. El **backend gestiona los tokens** y los almacena en cookies HttpOnly
3. El **frontend envía cookies automáticamente** con cada petición
4. El **backend valida tokens** y protege endpoints

### Flujo de Autenticación

```
┌─────────┐         ┌──────────────┐         ┌──────────┐
│ Angular │         │ Spring Boot  │         │ Keycloak │
│  :4200  │         │    :8081     │         │  :9090   │
└────┬────┘         └──────┬───────┘         └────┬─────┘
     │                     │                      │
     │ 1. GET /api/auth/login                    │
     ├────────────────────>│                      │
     │                     │ 2. OAuth2 redirect   │
     │                     ├─────────────────────>│
     │ 3. Login form       │                      │
     │<────────────────────┼──────────────────────┤
     │                     │                      │
     │ 4. Credentials      │                      │
     ├─────────────────────┼─────────────────────>│
     │                     │ 5. Authorization code│
     │                     │<─────────────────────┤
     │                     │                      │
     │                     │ 6. Exchange code for JWT
     │                     ├─────────────────────>│
     │                     │<─────────────────────┤
     │                     │ 7. JWT token         │
     │                     │                      │
     │ 8. Set-Cookie: ACCESS_TOKEN (HttpOnly)    │
     │<────────────────────┤                      │
     │                     │                      │
     │ 9. GET /api/user/me │                      │
     │    Cookie: ACCESS_TOKEN                    │
     ├────────────────────>│                      │
     │                     │ 10. Validate JWT     │
     │                     │ 11. Extract roles    │
     │<────────────────────┤                      │
     │ 12. User data       │                      │
```

### Características de Seguridad

✅ **Cookies HttpOnly**: JavaScript no puede acceder al token
✅ **SameSite=Strict**: Protección automática contra CSRF
✅ **CORS configurado**: Solo origins específicos permitidos
✅ **Validación dual**: Frontend (guards) + Backend (@PreAuthorize)
✅ **Session Management**: STATEFUL para cookies de sesión
✅ **Role-Based Access Control**: Roles de Keycloak extraídos automáticamente

---

## 📁 Estructura del Proyecto

```
keycloak-spring-demo/
├── src/main/java/com/example/keycloak/
│   ├── config/
│   │   ├── SecurityConfig.java              # Configuración BFF
│   │   └── OAuth2LoginSuccessHandler.java   # Gestión de cookies
│   ├── controller/
│   │   ├── AuthController.java              # Endpoints de autenticación
│   │   ├── UserController.java              # Endpoints para usuarios
│   │   ├── AdminController.java             # Endpoints para admins
│   │   └── PublicController.java            # Endpoints públicos
│   ├── filter/
│   │   └── JwtCookieFilter.java             # Extrae JWT de cookies
│   └── model/
│       └── UserInfo.java                    # DTOs
│
├── frontend/src/app/
│   ├── core/
│   │   ├── models/user.model.ts             # Interfaces TypeScript
│   │   ├── services/auth.service.ts         # Servicio de autenticación
│   │   ├── guards/auth.guard.ts             # Guards de rutas
│   │   └── interceptors/auth.interceptor.ts # withCredentials
│   ├── features/
│   │   ├── login/                           # Componente de login
│   │   └── dashboard/                       # Dashboard del usuario
│   ├── app.config.ts                        # Configuración de Angular
│   └── app.routes.ts                        # Rutas protegidas
│
├──  BACK_BFF.md                             # Documentación del backend
├──  FRONT_BFF.md                            # Documentación del frontend
└──  README.md                               # Este archivo
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

**Configurar Keycloak:**
1. Acceder a `http://localhost:9090`
2. Login con admin/admin
3. Crear realm: `mi-realm`
4. Crear client: `spring-boot-client` (Confidential)
5. Configurar:
   - Valid Redirect URIs: `http://localhost:8081/*`
   - Web Origins: `http://localhost:4200`
6. Crear roles: `user`, `admin`
7. Crear usuario de prueba y asignar roles

### Paso 2: Ejecutar Backend

```bash
# Actualizar client-secret en application.yml
mvn clean install
mvn spring-boot:run
```

Verificar: `http://localhost:8081/public/status`

### Paso 3: Ejecutar Frontend

```bash
cd frontend
npm install
npm start
```

Verificar: `http://localhost:4200`

### Paso 4: Probar

1. Navegar a `http://localhost:4200`
2. Click en "Login con Keycloak"
3. Autenticarse en Keycloak
4. Verificar dashboard con información del usuario
5. Abrir DevTools → Application → Cookies
6. Verificar cookie `ACCESS_TOKEN` con HttpOnly=true

---

## 🔐 Endpoints de la API

### Autenticación

| Endpoint | Método | Acceso | Descripción |
|----------|--------|--------|-------------|
| `/api/auth/login` | GET | Público | Inicia OAuth2 con Keycloak |
| `/api/auth/status` | GET | Público | Verifica sesión activa |
| `/api/auth/logout` | POST | Autenticado | Cierra sesión |

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

## 📚 Documentación Detallada

### Backend

Ver **[docs/backend/README.md](docs/backend/BACK_BFF.md)** para:
- Arquitectura del backend
- Configuración de Spring Security
- Gestión de cookies HttpOnly
- Extracción de roles de Keycloak
- Testing y troubleshooting

### Frontend

Ver **[docs/frontend/README.md](docs/frontend/FRONT_BFF.md)** para:
- Arquitectura del frontend
- Servicios y guards de Angular
- Componentes y routing
- Testing y troubleshooting

---

## 🔧 Configuración

### Puertos

- **Keycloak**: 9090 (Docker: 9090:8080)
- **Spring Boot**: 8081
- **Angular**: 4200

### Variables de Entorno

**Backend** (`application.yml`):
```yaml
app:
  frontend:
    url: http://localhost:4200
  cookie:
    secure: false  # true en producción (HTTPS)
    max-age: 3600  # 1 hora
```

**Frontend**:
- API URL: `http://localhost:8081/api` (hardcoded en `auth.service.ts`)
- Para producción: crear environment files

---

## ✅ Checklist de Producción

### Backend
- [ ] Cambiar `cookie.secure: true` (requiere HTTPS)
- [ ] Actualizar CORS a dominio de producción
- [ ] Client-secret como variable de entorno
- [ ] Habilitar CSRF
- [ ] Logs a nivel INFO

### Frontend
- [ ] Crear environment files (prod/dev)
- [ ] API URL dinámica desde environment
- [ ] Build optimizado: `ng build --configuration production`
- [ ] Content Security Policy

### Keycloak
- [ ] Valid Redirect URIs de producción
- [ ] Web Origins de producción
- [ ] Habilitar HTTPS
- [ ] Backup de configuración

---

## 🎓 Conceptos Aprendidos

### Seguridad
- OAuth2 Authorization Code Flow
- Patrón Backend for Frontend (BFF)
- Cookies HttpOnly vs localStorage
- CSRF y SameSite cookies
- CORS con credentials
- JWT validation y RBAC

### Tecnologías
- Spring Security OAuth2 Client + Resource Server
- Keycloak Integration
- Angular Standalone Components
- Angular Signals
- Functional Guards e Interceptors
- RxJS Observables

---

## 🚧 Mejoras Futuras (Opcional)

1. **Refresh Token Automático Avanzado**
   - Interceptor que detecta 401 y refresh transparente

2. **Logout Global SSO**
   - Implementar `end_session_endpoint` de Keycloak
   - Logout en todas las aplicaciones del SSO

3. **HTTPS en Desarrollo**
   - Certificados self-signed
   - Cookie Secure=true

4. **Página de Admin**
   - Solo accesible con rol ADMIN
   - Uso de `roleGuard('ROLE_ADMIN')`

---

## ❓ Troubleshooting

### Error CORS
- Verificar que Spring Boot está corriendo
- Verificar configuración CORS en `SecurityConfig.java`
- Restart Spring Boot

### Cookie no se crea
- Verificar logs: "Cookie de sesión creada exitosamente"
- Verificar `SameSite=Strict` en `OAuth2LoginSuccessHandler`
- Limpiar cookies del navegador

### 401 en peticiones
- Verificar cookie en DevTools
- Verificar `withCredentials: true` en Angular
- Verificar CORS permite credentials

---

## 📖 Referencias

- [Spring Security OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Angular Security Guide](https://angular.dev/best-practices/security)
- [BFF Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)

# API Resource Server - Machine to Machine (M2M)

> **Rama:** `oauth2-resource-server`
> **Propósito:** API REST STATELESS que valida tokens JWT de Keycloak para comunicación machine-to-machine

---

## 🎯 ¿Qué es esta rama?

Esta rama implementa una **API Resource Server pura** que:
- ✅ **Valida tokens JWT** de Keycloak (no los genera)
- ✅ **Arquitectura STATELESS** (sin sesiones HTTP)
- ✅ **Extrae roles** de Keycloak y los convierte en authorities de Spring Security
- ✅ **Protege endpoints** con control de acceso basado en roles (RBAC)
- ✅ **Soporta comunicación machine-to-machine** (M2M)

**No implementa:** OAuth2 Login, cookies, redirecciones, ni gestión de sesiones.

---

## 🏗️ Arquitectura

```
┌─────────────┐   1. Obtener Token     ┌──────────┐
│  Cliente    │ ─────────────────────> │ Keycloak │
│  (App/User) │ <───────────────────── │  :9090   │
└─────────────┘   2. Recibe JWT        └──────────┘
       │
       │ 3. GET /api/user/me
       │    Authorization: Bearer {token}
       ▼
┌─────────────┐
│ Spring Boot │   4. Valida JWT con
│    :8081    │      claves públicas
│  (Esta API) │   5. Extrae roles
└─────────────┘   6. Responde datos
```

---

## 🚀 Inicio Rápido

### 1. Iniciar Keycloak

```bash
docker run -p 9090:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

**Nota:** Mapeamos el puerto `9090` del host al `8080` del contenedor.

### 2. Configurar Keycloak

Sigue la guía detallada: **[KEYCLOAK-SETUP-M2M.md](KEYCLOAK-SETUP-M2M.md)**

Resumen:
1. Acceder a http://localhost:9090/admin
2. Crear realm: `mi-realm`
3. Crear client: `spring-boot-client`
   - Client authentication: ON
   - Service accounts roles: ON (para M2M)
   - Direct access grants: ON (para testing)
4. Asignar roles al service account: `user`, `admin`
5. Copiar el Client Secret

### 3. Actualizar application.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/mi-realm
```

Ya está configurado correctamente en esta rama.

### 4. Ejecutar la API

```bash
./mvnw spring-boot:run
```

La API estará disponible en: http://localhost:8081

---

## 🔐 Flujos de Autenticación

### Opción 1: Client Credentials (M2M recomendado)

**Uso:** Comunicación servicio-a-servicio sin usuario humano.

```bash
curl -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-boot-client" \
  -d "client_secret=TU-SECRET-AQUI"
```

**Token representa:** Service account (`service-account-spring-boot-client`)
**Roles:** Los asignados manualmente al service account en Keycloak

### Opción 2: Password Credentials (Testing)

**Uso:** Testing rápido con usuario real (⚠️ NO para producción).

```bash
curl -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=spring-boot-client" \
  -d "client_secret=TU-SECRET-AQUI" \
  -d "username=usuario1" \
  -d "password=password123"
```

**Token representa:** Usuario real (`usuario1`)
**Roles:** Los del usuario en Keycloak

---

## 🧪 Probar la API

### Pruebas Manuales

#### 1. Obtener Token

```bash
# Guardar respuesta
TOKEN_RESPONSE=$(curl -s -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=spring-boot-client" \
  -d "client_secret=TU-SECRET")

# Extraer access_token
TOKEN=$(echo $TOKEN_RESPONSE | jq -r '.access_token')
```

#### 2. Endpoint Público (sin token)

```bash
curl http://localhost:8081/public/hello
```

**Respuesta esperada:**
```json
{
  "message": "Hola! Este es un endpoint público",
  "timestamp": "2024-11-23T10:00:00"
}
```

#### 3. Endpoints Protegidos (requieren token)

```bash
# Ver información del usuario/servicio autenticado
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer $TOKEN"

# Ver todos los datos del token
curl http://localhost:8081/api/user/token-info \
  -H "Authorization: Bearer $TOKEN"

# Dashboard de usuario (requiere ROLE_USER)
curl http://localhost:8081/api/user/dashboard \
  -H "Authorization: Bearer $TOKEN"

# Dashboard de admin (requiere ROLE_ADMIN)
curl http://localhost:8081/api/admin/dashboard \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🔑 Roles y Autorización

### Extracción de Roles

La API extrae roles de dos lugares del token JWT:

**1. Roles del Realm** (`realm_access.roles`):
```json
{
  "realm_access": {
    "roles": ["user", "admin"]
  }
}
```

**2. Roles del Cliente** (`resource_access.{client-id}.roles`):
```json
{
  "resource_access": {
    "spring-boot-client": {
      "roles": ["manager"]
    }
  }
}
```

### Conversión a Spring Security

Keycloak → Spring Security:
- `user` → `ROLE_USER`
- `admin` → `ROLE_ADMIN`

### Protección de Endpoints

```java
@PreAuthorize("hasRole('USER')")   // Requiere ROLE_USER
@PreAuthorize("hasRole('ADMIN')")  // Requiere ROLE_ADMIN
```

### Configuración de Roles

**Para Service Account (Client Credentials):**
1. Keycloak → Clients → spring-boot-client
2. Service accounts roles → Assign role
3. Seleccionar: `user`, `admin`, etc.

**Para Usuarios (Password Credentials):**
1. Keycloak → Users → usuario1
2. Role mapping → Assign role
3. Seleccionar roles necesarios

---

## 📂 Estructura del Proyecto

```
src/main/java/com/example/keycloak/
├── config/
│   └── SecurityConfig.java          # Configuración de seguridad STATELESS
├── controller/
│   ├── PublicController.java        # Endpoints públicos
│   ├── UserController.java          # Endpoints con ROLE_USER
│   └── AdminController.java         # Endpoints con ROLE_ADMIN
└── model/
    └── UserInfo.java                # DTO de información de usuario

src/main/resources/
└── application.yml                  # Configuración de Resource Server

Documentación:
├── README.md                        # Este archivo
├── KEYCLOAK-SETUP-M2M.md           # Configuración detallada de Keycloak
```

---

## ⚙️ Configuración Técnica

### application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: keycloak-spring-demo-api
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:9090/realms/mi-realm/protocol/openid-connect/certs
          issuer-uri: http://localhost:9090/realms/mi-realm
```

### SecurityConfig.java

**Características:**
- ✅ STATELESS (SessionCreationPolicy.STATELESS)
- ✅ oauth2ResourceServer (valida JWT)
- ❌ NO oauth2Login (no gestiona autenticación)
- ❌ CSRF deshabilitado (apropiado para APIs REST)
- ✅ Extracción de roles de `realm_access` y `resource_access`

---

## 🐛 Troubleshooting

### Error: 401 Unauthorized

**Causa:** Token inválido, expirado o ausente.

**Solución:**
```bash
# Verificar que envías el header correcto
-H "Authorization: Bearer {token}"

# Obtener un nuevo token
curl -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token ...
```

### Error: 403 Forbidden

**Causa:** Token válido pero sin el rol requerido.

**Solución:**
```bash
# Ver qué roles tienes
curl http://localhost:8081/api/user/token-info -H "Authorization: Bearer $TOKEN"

# Asignar roles en Keycloak:
# - Service Account: Clients → Service accounts roles
# - Usuario: Users → Role mapping
```

### Error: Invalid token signature

**Causa:** URL de Keycloak incorrecta o puerto equivocado.

**Solución:**
```bash
# Verificar que Keycloak está en puerto 9090
curl http://localhost:9090/realms/mi-realm/.well-known/openid-configuration

# Verificar application.yml tiene:
issuer-uri: http://localhost:9090/realms/mi-realm
```

### Service Account sin roles

**Causa:** No se asignaron roles al service account.

**Solución:**
1. Keycloak → Clients → spring-boot-client
2. Service accounts roles → Assign role
3. Asignar: `user`, `admin`
4. Obtener nuevo token

---

## 📖 Documentación Adicional

- **[KEYCLOAK-SETUP-M2M.md](KEYCLOAK-SETUP-M2M.md)** - Guía paso a paso de configuración de Keycloak
- **[TEST-API.ps1](TEST-API.ps1)** - Script de pruebas automatizadas (Windows)
- **[TEST-API.sh](TEST-API.sh)** - Script de pruebas automatizadas (Linux/Mac)

---

## 🎓 Conceptos Aprendidos

✅ **API Resource Server** - Validar tokens JWT sin gestionarlos
✅ **STATELESS** - Arquitectura sin sesiones HTTP
✅ **Client Credentials** - Flujo M2M para servicios
✅ **Password Credentials** - Flujo para testing (no producción)
✅ **Service Account** - Usuario virtual para servicios
✅ **RBAC** - Control de acceso basado en roles
✅ **JWT Validation** - Validación con claves públicas (JWKS)
✅ **Roles Extraction** - De Keycloak a Spring Security

---

## 🔗 Otras Ramas

- **main** - Configuración base (por definir)
- **oauth2-resource-server** - API Resource Server STATELESS para M2M (esta rama)
- **oauth2-bff** - Patrón BFF para SPAs con cookies HttpOnly y Authorization Code Flow

---

## 📝 Notas Importantes

### Puertos
- **Keycloak:** 9090 (host) → 8080 (contenedor Docker)
- **API Spring Boot:** 8081

### Client Secret
⚠️ **NO subir a git** - Usar variables de entorno en producción:
```yaml
client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

### Diferencia con BFF
Esta rama **NO implementa BFF**. Para patrón BFF (Backend for Frontend) con cookies y sesiones, ver rama `oauth2-bff`.

---

## ✅ Checklist de Configuración

- [ ] Keycloak corriendo en puerto 9090
- [ ] Realm creado: `mi-realm`
- [ ] Client creado: `spring-boot-client`
- [ ] Client authentication: ON
- [ ] Service accounts roles: ON
- [ ] Roles asignados (user, admin)
- [ ] Client secret copiado
- [ ] API Spring Boot corriendo en 8081
- [ ] Token obtenido exitosamente
- [ ] Endpoint `/api/user/me` responde correctamente

---

**¿Preguntas?** Revisa [KEYCLOAK-SETUP-M2M.md](KEYCLOAK-SETUP-M2M.md) para más detalles.

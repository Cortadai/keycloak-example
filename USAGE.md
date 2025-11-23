# Guía de Uso - Ejemplos Básicos

Ejemplos básicos para usar y probar la aplicación.

> **Rama:** `main` - Versión educativa básica
> **Nivel:** 🌱 Principiante

---

## 📋 Tabla de Contenidos

1. [Iniciar](#1-iniciar)
2. [Usuarios de Prueba](#2-usuarios-de-prueba)
3. [Ejemplos con cURL](#3-ejemplos-con-curl)
4. [Endpoints](#4-endpoints)
5. [Inspeccionar Token JWT](#5-inspeccionar-token-jwt)

---

## 1. Iniciar

### Verificaciones

```bash
# Keycloak disponible (puerto 9090)
curl http://localhost:9090

# Spring Boot corriendo (puerto 8081)
curl http://localhost:8081/public/hello
```

### Ejecutar

```bash
./mvnw spring-boot:run
```

---

## 2. Usuarios de Prueba

| Usuario | Contraseña | Roles |
|---------|------------|-------|
| usuario1 | password123 | user |
| admin1 | admin123 | user, admin |

---

## 3. Ejemplos con cURL

### 3.1 Endpoint Público

```bash
curl http://localhost:8081/public/hello
```

**Respuesta:**
```json
{
  "message": "¡Hola! Este es un endpoint público.",
  "info": "No necesitas estar autenticado para ver esto."
}
```

### 3.2 Obtener Token

**Usuario regular:**
```bash
curl -X POST 'http://localhost:9090/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_SECRET' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

**Admin:**
```bash
curl -X POST 'http://localhost:9090/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_SECRET' \
  -d 'grant_type=password' \
  -d 'username=admin1' \
  -d 'password=admin123'
```

**Respuesta:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 300,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "token_type": "Bearer"
}
```

**Guardar token (Linux/Mac):**
```bash
export TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI..."
```

**Guardar token (Windows PowerShell):**
```powershell
$TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI..."
```

### 3.3 Endpoint Protegido (USER)

```bash
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer $TOKEN"
```

**Respuesta:**
```json
{
  "username": "usuario1",
  "email": "usuario1@example.com",
  "roles": ["ROLE_USER"],
  "authenticated": true
}
```

### 3.4 Endpoint Admin (ADMIN)

```bash
# Primero obtén token de admin
export ADMIN_TOKEN="..."

# Luego accede
curl http://localhost:8081/api/admin/dashboard \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

**Respuesta:**
```json
{
  "message": "Bienvenido al panel de administración",
  "admin": "admin1"
}
```

### 3.5 Probar Control de Acceso (403)

Intenta acceder a admin con token de usuario regular:

```bash
curl -w "\nHTTP: %{http_code}\n" \
  http://localhost:8081/api/admin/dashboard \
  -H "Authorization: Bearer $TOKEN"
```

**Resultado:** HTTP 403 Forbidden

---

## 4. Endpoints

### 4.1 Públicos (Sin Token)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/public/hello` | Mensaje de bienvenida |
| GET | `/public/info` | Información de la app |

**Ejemplo:**
```bash
curl http://localhost:8081/public/info
```

### 4.2 Protegidos - Usuario (ROLE_USER)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/user/me` | Info del usuario |
| GET | `/api/user/dashboard` | Dashboard |
| GET | `/api/user/profile` | Perfil |

**Ejemplo:**
```bash
curl http://localhost:8081/api/user/profile \
  -H "Authorization: Bearer $TOKEN"
```

### 4.3 Protegidos - Admin (ROLE_ADMIN)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Dashboard admin |
| GET | `/api/admin/users` | Lista de usuarios |

**Ejemplo:**
```bash
curl http://localhost:8081/api/admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 5. Inspeccionar Token JWT

### jwt.io

1. Ve a https://jwt.io
2. Pega tu `access_token`
3. Verás el payload:

```json
{
  "preferred_username": "usuario1",
  "email": "usuario1@example.com",
  "realm_access": {
    "roles": ["user"]
  }
}
```

### Comando (Linux/Mac)

```bash
echo $TOKEN | cut -d. -f2 | base64 -d | jq
```

---

## 6. Windows PowerShell

### Obtener Token

```powershell
$body = @{
    client_id = 'spring-boot-client'
    client_secret = 'TU_SECRET'
    grant_type = 'password'
    username = 'usuario1'
    password = 'password123'
}

$response = Invoke-RestMethod `
  -Uri 'http://localhost:9090/realms/mi-realm/protocol/openid-connect/token' `
  -Method Post `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body $body

$TOKEN = $response.access_token
Write-Host "Token obtenido"
```

### Probar Endpoint

```powershell
$headers = @{
    Authorization = "Bearer $TOKEN"
}

Invoke-RestMethod `
  -Uri 'http://localhost:8081/api/user/me' `
  -Headers $headers
```

---

## 7. Casos de Prueba

| # | Escenario | Token | Endpoint | Resultado |
|---|-----------|-------|----------|-----------|
| 1 | Público | Ninguno | `/public/hello` | ✅ 200 OK |
| 2 | Usuario | usuario1 | `/api/user/me` | ✅ 200 OK |
| 3 | Admin | admin1 | `/api/admin/dashboard` | ✅ 200 OK |
| 4 | Sin rol | usuario1 | `/api/admin/dashboard` | ❌ 403 |
| 5 | Sin token | Ninguno | `/api/user/me` | ❌ 401 |

---

## 💡 Tips

### Formatear JSON

```bash
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Ver Headers

```bash
curl -i http://localhost:8081/public/hello
```

### Debugging

Si tienes problemas:

1. **Verifica que Keycloak esté corriendo:**
   ```bash
   curl http://localhost:9090
   ```

2. **Verifica que el client secret sea correcto:**
   - Ve a Keycloak → Clients → spring-boot-client → Credentials

3. **Verifica que el token no haya expirado:**
   - Los tokens duran 5 minutos (300 segundos) por defecto
   - Obtén un nuevo token si es necesario

4. **Revisa los logs de Spring Boot:**
   - Busca mensajes de error en la consola

---

## 🎯 ¿Qué Aprendiste?

Con estos ejemplos ahora sabes:

✅ Cómo obtener un token JWT desde Keycloak
✅ Cómo enviar el token en el header Authorization
✅ Diferencia entre endpoints públicos y protegidos
✅ Cómo funciona el control de acceso basado en roles (RBAC)
✅ Cómo probar diferentes escenarios (401, 403, 200)
✅ Arquitectura STATELESS (cada request incluye el token)

---

## 🚀 Siguiente Nivel

Para funcionalidades más avanzadas:

### Rama `oauth2-resource-server`

**Qué añade:**
- Client Credentials (M2M)
- Service Accounts

**Cuándo usarla:**
- Comunicación servicio-a-servicio
- APIs M2M

```bash
git checkout oauth2-resource-server
```

### Rama `oauth2-spa-pkce`

Qué añade:
- Authorization Code Flow + PKCE (sin Client Secret)
- Frontend Angular completo integrado con angular-oauth2-oidc
- Tokens JWT gestionados en memoria (sin localStorage)
- Logout automático cuando expira el token

Cuándo usarla:
- Proyectos donde la simplicidad de despliegue es importante (frontend estático + API)

```bash
git checkout oauth2-spa-pkce
```

### Rama `oauth2-bff-cookies`

**Qué añade:**
- Authorization Code Flow
- Patrón BFF
- Login desde navegador
- Cookies HttpOnly

**Cuándo usarla:**
- SPAs modernas (React, Angular)

```bash
git checkout oauth2-bff-cookies
```

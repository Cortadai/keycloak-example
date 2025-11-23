# Guía de Uso - Ejemplos Básicos

Ejemplos básicos para usar y probar la aplicación.

> 💡 **Tip**: Para ejemplos más completos y scripts automatizados, ver la rama **[oauth2-authorization-code](../../tree/oauth2-authorization-code)**

## 📋 Tabla de Contenidos

1. [Iniciar](#1-iniciar)
2. [Usuarios de Prueba](#2-usuarios-de-prueba)
3. [Ejemplos con cURL](#3-ejemplos-con-curl)
4. [Endpoints](#4-endpoints)

---

## 1. Iniciar

### Verificaciones

```bash
# Keycloak disponible
curl http://localhost:8080

# Spring Boot corriendo
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
| usuario1 | password123 | USER |
| admin1 | admin123 | USER, ADMIN |

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
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_SECRET' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

**Admin:**
```bash
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
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

## 5. Windows PowerShell

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
  -Uri 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' `
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

## 6. Inspeccionar Token JWT

### jwt.io

1. Ve a https://jwt.io
2. Pega tu `access_token`
3. Verás el payload:

```json
{
  "preferred_username": "usuario1",
  "email": "usuario1@example.com",
  "realm_access": {
    "roles": ["USER"]
  }
}
```

### Comando (Linux/Mac)

```bash
echo $TOKEN | cut -d. -f2 | base64 -d | jq
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
1. Verifica que Keycloak esté corriendo
2. Verifica que el client secret sea correcto
3. Verifica que el token no haya expirado
4. Revisa los logs de Spring Boot

---

## 🚀 Siguiente Nivel

Para testing más avanzado con:
- Scripts automatizados
- Colección de Postman completa
- OAuth2 Login desde navegador
- Refresh tokens

Revisa la rama **[oauth2-authorization-code](../../tree/oauth2-authorization-code)**

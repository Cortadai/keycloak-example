# Ejemplos de Prueba - Spring Boot + Keycloak

Esta guía contiene ejemplos prácticos para probar todos los endpoints de la aplicación.

## Usuarios de Prueba

Según la configuración en Keycloak:

| Usuario | Contraseña | Roles | Descripción |
|---------|------------|-------|-------------|
| usuario1 | password123 | USER | Usuario regular |
| admin1 | admin123 | USER, ADMIN | Administrador |

## 1. Obtener Token de Acceso

### Con cURL (Linux/Mac/Git Bash):

```bash
# Token para usuario1 (rol USER)
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_CLIENT_SECRET' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

```bash
# Token para admin1 (roles USER y ADMIN)
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_CLIENT_SECRET' \
  -d 'grant_type=password' \
  -d 'username=admin1' \
  -d 'password=admin123'
```

### Con PowerShell (Windows):

```powershell
# Token para usuario1
$body = @{
    client_id = 'spring-boot-client'
    client_secret = 'TU_CLIENT_SECRET'
    grant_type = 'password'
    username = 'usuario1'
    password = 'password123'
}

$response = Invoke-RestMethod -Uri 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' `
  -Method Post `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body $body

$token = $response.access_token
Write-Host "Token: $token"
```

### Respuesta Esperada:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
  "expires_in": 300,
  "refresh_expires_in": 1800,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI...",
  "token_type": "Bearer",
  "not-before-policy": 0,
  "session_state": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "scope": "profile email"
}
```

**Importante**: Copia el valor de `access_token` para usarlo en las siguientes pruebas.

## 2. Endpoints Públicos (Sin Autenticación)

### 2.1 Hello Endpoint

```bash
curl http://localhost:8081/public/hello
```

**Respuesta:**
```json
{
  "message": "¡Hola! Este es un endpoint público.",
  "info": "No necesitas estar autenticado para ver esto.",
  "timestamp": "2024-11-22T10:30:00"
}
```

### 2.2 Info Endpoint

```bash
curl http://localhost:8081/public/info
```

### 2.3 Status Endpoint

```bash
curl http://localhost:8081/public/status
```

## 3. Endpoints de Usuario (Requiere Rol USER)

**Importante**: Reemplaza `{TOKEN}` con tu access_token.

### 3.1 Información del Usuario Actual

```bash
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {TOKEN}"
```

**Respuesta esperada:**
```json
{
  "username": "usuario1",
  "email": "usuario1@example.com",
  "name": "Juan Pérez",
  "roles": [
    "ROLE_USER"
  ],
  "authenticated": true,
  "message": "Información del usuario autenticado"
}
```

### 3.2 Dashboard del Usuario

```bash
curl http://localhost:8081/api/user/dashboard \
  -H "Authorization: Bearer {TOKEN}"
```

### 3.3 Información del Token JWT

```bash
curl http://localhost:8081/api/user/token-info \
  -H "Authorization: Bearer {TOKEN}"
```

**Útil para**: Ver todos los claims del token (debugging).

### 3.4 Perfil del Usuario

```bash
curl http://localhost:8081/api/user/profile \
  -H "Authorization: Bearer {TOKEN}"
```

## 4. Endpoints de Admin (Requiere Rol ADMIN)

**Importante**: Usa el token de admin1 para estos endpoints.

### 4.1 Dashboard de Administración

```bash
curl http://localhost:8081/api/admin/dashboard \
  -H "Authorization: Bearer {ADMIN_TOKEN}"
```

**Respuesta esperada:**
```json
{
  "message": "Bienvenido al panel de administración",
  "admin": "admin1",
  "info": "Este endpoint solo es accesible para usuarios con rol ADMIN",
  "timestamp": "2024-11-22T10:30:00",
  "statistics": {
    "totalUsers": 150,
    "activeUsers": 120,
    "totalOrders": 500,
    "pendingOrders": 25
  }
}
```

### 4.2 Lista de Usuarios

```bash
curl http://localhost:8081/api/admin/users \
  -H "Authorization: Bearer {ADMIN_TOKEN}"
```

### 4.3 Estadísticas del Sistema

```bash
curl http://localhost:8081/api/admin/stats \
  -H "Authorization: Bearer {ADMIN_TOKEN}"
```

### 4.4 Actualizar Configuración (PUT)

```bash
curl -X PUT http://localhost:8081/api/admin/settings \
  -H "Authorization: Bearer {ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "maintenance_mode": false,
    "max_users": 1000,
    "feature_flags": {
      "new_ui": true,
      "beta_features": false
    }
  }'
```

### 4.5 Operación Peligrosa (DELETE)

```bash
curl -X DELETE http://localhost:8081/api/admin/dangerous-operation \
  -H "Authorization: Bearer {ADMIN_TOKEN}"
```

## 5. Pruebas de Control de Acceso

### 5.1 Usuario sin rol suficiente (403 Forbidden)

Intenta acceder a un endpoint de admin con token de usuario regular:

```bash
curl http://localhost:8081/api/admin/dashboard \
  -H "Authorization: Bearer {USER_TOKEN}"
```

**Resultado esperado**: Error 403 Forbidden
```json
{
  "timestamp": "2024-11-22T10:30:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Forbidden",
  "path": "/api/admin/dashboard"
}
```

### 5.2 Sin token (401 Unauthorized)

Intenta acceder sin enviar el token:

```bash
curl http://localhost:8081/api/user/me
```

**Resultado esperado**: Error 401 Unauthorized

### 5.3 Token expirado

Los tokens expiran después de un tiempo (normalmente 5-15 minutos).

**Solución**: Obtén un nuevo token o usa el refresh_token.

## 6. Usar Refresh Token

Los refresh tokens te permiten obtener un nuevo access_token sin volver a ingresar credenciales:

```bash
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_CLIENT_SECRET' \
  -d 'grant_type=refresh_token' \
  -d 'refresh_token=TU_REFRESH_TOKEN'
```

## 7. Colección de Postman

### Importar en Postman:

1. Abre Postman
2. Click en "Import"
3. Crea una nueva colección
4. Crea las siguientes requests:

#### Request 1: Obtener Token (Usuario)

- **Name**: Get Token - User
- **Method**: POST
- **URL**: `http://localhost:8080/realms/mi-realm/protocol/openid-connect/token`
- **Body** (x-www-form-urlencoded):
  - `client_id`: spring-boot-client
  - `client_secret`: [tu secret]
  - `grant_type`: password
  - `username`: usuario1
  - `password`: password123
- **Tests** (para guardar el token automáticamente):
```javascript
pm.test("Token recibido", function () {
    var jsonData = pm.response.json();
    pm.environment.set("access_token", jsonData.access_token);
});
```

#### Request 2: User Info

- **Name**: Get User Info
- **Method**: GET
- **URL**: `http://localhost:8081/api/user/me`
- **Headers**:
  - `Authorization`: `Bearer {{access_token}}`

#### Request 3: Admin Dashboard

- **Name**: Admin Dashboard
- **Method**: GET
- **URL**: `http://localhost:8081/api/admin/dashboard`
- **Headers**:
  - `Authorization`: `Bearer {{access_token}}`

## 8. Script Completo de Prueba (Bash)

Guarda esto en `test.sh`:

```bash
#!/bin/bash

# Configuración
CLIENT_SECRET="tu-client-secret"
KEYCLOAK_URL="http://localhost:8080/realms/mi-realm/protocol/openid-connect/token"
API_URL="http://localhost:8081"

echo "=== Probando Spring Boot + Keycloak ==="

# 1. Endpoint público
echo -e "\n1. Probando endpoint público..."
curl -s $API_URL/public/hello | jq

# 2. Obtener token de usuario
echo -e "\n2. Obteniendo token de usuario..."
USER_TOKEN=$(curl -s -X POST $KEYCLOAK_URL \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d "client_secret=$CLIENT_SECRET" \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123' | jq -r '.access_token')

echo "Token obtenido: ${USER_TOKEN:0:20}..."

# 3. Probar endpoint de usuario
echo -e "\n3. Probando endpoint de usuario..."
curl -s $API_URL/api/user/me \
  -H "Authorization: Bearer $USER_TOKEN" | jq

# 4. Obtener token de admin
echo -e "\n4. Obteniendo token de admin..."
ADMIN_TOKEN=$(curl -s -X POST $KEYCLOAK_URL \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d "client_secret=$CLIENT_SECRET" \
  -d 'grant_type=password' \
  -d 'username=admin1' \
  -d 'password=admin123' | jq -r '.access_token')

# 5. Probar endpoint de admin
echo -e "\n5. Probando endpoint de admin..."
curl -s $API_URL/api/admin/dashboard \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# 6. Probar acceso denegado
echo -e "\n6. Probando acceso denegado (usuario intentando acceder a admin)..."
curl -s -w "\nHTTP Status: %{http_code}\n" $API_URL/api/admin/dashboard \
  -H "Authorization: Bearer $USER_TOKEN"

echo -e "\n=== Pruebas completadas ==="
```

Ejecutar:
```bash
chmod +x test.sh
./test.sh
```

## 9. Inspeccionar el Token JWT

### Usando jwt.io

1. Ve a https://jwt.io
2. Pega tu access_token
3. Verás el contenido decodificado:

```json
{
  "exp": 1700000000,
  "iat": 1699999700,
  "jti": "abc-123-def",
  "iss": "http://localhost:8080/realms/mi-realm",
  "aud": "account",
  "sub": "user-id-123",
  "typ": "Bearer",
  "azp": "spring-boot-client",
  "session_state": "session-id",
  "acr": "1",
  "realm_access": {
    "roles": [
      "USER"
    ]
  },
  "resource_access": {
    "spring-boot-client": {
      "roles": [
        "USER"
      ]
    }
  },
  "scope": "profile email",
  "email_verified": true,
  "name": "Juan Pérez",
  "preferred_username": "usuario1",
  "email": "usuario1@example.com"
}
```

### Usando jq (línea de comandos)

```bash
# Decodificar el payload del JWT
echo $TOKEN | cut -d. -f2 | base64 -d | jq
```

## 10. Casos de Prueba Completos

| # | Escenario | Token | Endpoint | Resultado Esperado |
|---|-----------|-------|----------|-------------------|
| 1 | Acceso público | Ninguno | `/public/hello` | ✅ 200 OK |
| 2 | Usuario autenticado | usuario1 | `/api/user/me` | ✅ 200 OK |
| 3 | Admin autenticado | admin1 | `/api/admin/dashboard` | ✅ 200 OK |
| 4 | Usuario sin rol | usuario1 | `/api/admin/dashboard` | ❌ 403 Forbidden |
| 5 | Sin autenticar | Ninguno | `/api/user/me` | ❌ 401 Unauthorized |
| 6 | Token inválido | invalid | `/api/user/me` | ❌ 401 Unauthorized |

## Consejos

1. **Guarda los tokens**: Expiran en ~5-15 minutos. Guárdalos en variables de entorno.
2. **Usa jq**: Para formatear las respuestas JSON: `curl ... | jq`
3. **Debugging**: Usa el endpoint `/api/user/token-info` para ver qué contiene tu token
4. **Postman**: Crea un environment con el `access_token` para no copiar/pegar cada vez
5. **Browser**: Para OAuth2 login flow, abre http://localhost:8081/api/user/me en el navegador

## Troubleshooting

### No puedo obtener el token
- Verifica que Keycloak esté corriendo
- Verifica client_id y client_secret
- Verifica username y password

### Error 401 al usar el token
- El token puede haber expirado
- Verifica que estés usando el header correcto: `Authorization: Bearer {token}`

### Error 403 Forbidden
- El usuario no tiene el rol requerido
- Verifica los roles en Keycloak

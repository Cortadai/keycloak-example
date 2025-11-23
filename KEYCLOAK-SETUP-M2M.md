# Configuración de Keycloak para Machine-to-Machine

Guía paso a paso para configurar Keycloak para comunicación machine-to-machine con Client Credentials.

## 🎯 Objetivo

Configurar un cliente en Keycloak que permita a servicios/aplicaciones obtener tokens sin intervención humana.

## 📋 Requisitos Previos

- Keycloak corriendo en http://localhost:9090
- Realm creado: `mi-realm` (o el nombre que prefieras)
- Acceso de administrador a Keycloak

## 🔧 Paso 1: Crear el Client

1. **Acceder a la consola de administración**
   - URL: http://localhost:9090/admin
   - Usuario: admin (o el que hayas configurado)
   - Contraseña: tu contraseña de admin

2. **Seleccionar el Realm**
   - Click en el dropdown superior izquierdo
   - Seleccionar: `mi-realm`

3. **Crear nuevo Client**
   - Menú lateral: **Clients**
   - Click en **Create client**

### General Settings

```
Client type: OpenID Connect
Client ID: m2m-service
Name: Machine to Machine Service (opcional)
Description: Cliente para comunicación service-to-service (opcional)
```

- Click en **Next**

### Capability config

```
Client authentication: ON ✅
  (Esto lo hace "Confidential" - generará un secret)

Authorization: OFF ❌
  (No necesario para M2M básico)

Authentication flow:
  Standard flow: OFF ❌
  Direct access grants: OFF ❌
  Implicit flow: OFF ❌
  Service accounts roles: ON ✅  ⬅️ IMPORTANTE para M2M
  OAuth 2.0 Device Authorization Grant: OFF ❌
  OIDC CIBA Grant: OFF ❌
```

- Click en **Next**

### Login settings

```
Root URL: (dejar vacío)
Home URL: (dejar vacío)
Valid redirect URIs: (dejar vacío - no hay redirecciones en M2M)
Valid post logout redirect URIs: (dejar vacío)
Web origins: (dejar vacío)
```

- Click en **Save**

## 🔑 Paso 2: Obtener el Client Secret

1. **Ir a la pestaña "Credentials"**
   - En la página del client recién creado
   - Verás: **Client secret**

2. **Copiar el secret**
   ```
   Client secret: a1b2c3d4-e5f6-7890-abcd-ef1234567890
   ```

3. **Guardar el secret de forma segura**
   - Este secret es como una contraseña
   - ⚠️ No lo compartas ni lo subas a git
   - Úsalo en tu aplicación cliente

## 👤 Paso 3: Configurar Service Account

El "Service Account" es un usuario virtual que representa al servicio.

1. **Ir a la pestaña "Service accounts roles"**
   - En la página del client
   - Verás el username: `service-account-m2m-service`

2. **Asignar roles del Realm**
   - Click en **Assign role**
   - Click en **Filter by realm roles** (en el dropdown)
   - Buscar y seleccionar: `user`
   - Click en **Assign**

3. **Asignar roles de Cliente (opcional)**
   - Click en **Assign role**
   - Click en **Filter by clients**
   - Seleccionar el client que tenga los roles que necesitas
   - Seleccionar los roles necesarios
   - Click en **Assign**

4. **Asignar rol de Admin (si es necesario)**
   - Click en **Assign role**
   - Click en **Filter by realm roles**
   - Buscar y seleccionar: `admin`
   - Click en **Assign**

### Roles Comunes

- `user` - Acceso básico de usuario
- `admin` - Acceso de administrador
- `offline_access` - Para obtener refresh tokens (opcional)

## ✅ Paso 4: Verificar la Configuración

### Opción A: Desde la UI de Keycloak

1. **Service account roles**
   - Verificar que los roles están asignados
   - Deberías ver: `user`, `admin`, etc.

2. **Settings**
   - Verificar que "Service accounts roles" está habilitado

### Opción B: Obtener un Token de Prueba

```bash
curl -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=m2m-service" \
  -d "client_secret=TU-SECRET-AQUI"
```

**Respuesta esperada:**
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cC...",
  "expires_in": 300,
  "refresh_expires_in": 0,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "profile email"
}
```

Si obtienes el token, ✅ la configuración es correcta.

### Decodificar el Token

Visita https://jwt.io y pega el `access_token` para ver su contenido.

Deberías ver algo como:
```json
{
  "exp": 1700000000,
  "iat": 1699999700,
  "jti": "uuid-here",
  "iss": "http://localhost:9090/realms/mi-realm",
  "sub": "uuid-of-service-account",
  "typ": "Bearer",
  "azp": "m2m-service",
  "realm_access": {
    "roles": [
      "user",
      "admin"
    ]
  },
  "resource_access": {
    "account": {
      "roles": [
        "manage-account",
        "view-profile"
      ]
    }
  },
  "scope": "profile email",
  "clientId": "m2m-service",
  "clientHost": "127.0.0.1",
  "preferred_username": "service-account-m2m-service"
}
```

## 🎓 Conceptos Importantes

### Service Account vs Usuario Regular

| Aspecto | Service Account | Usuario Regular |
|---------|----------------|-----------------|
| **Creación** | Automática con el cliente | Manual por admin |
| **Username** | `service-account-{client-id}` | Elegido por usuario |
| **Login** | Con client_id + secret | Con username + password |
| **Uso** | Machine-to-machine | Usuario humano |
| **Password** | No tiene (usa client secret) | Sí tiene |

### Grant Types

**Client Credentials (M2M):**
```bash
grant_type=client_credentials
client_id=m2m-service
client_secret=secret
```
✅ Recomendado para servicios backend

**Resource Owner Password (Testing only):**
```bash
grant_type=password
client_id=client
username=usuario
password=pass
```
⚠️ Solo para desarrollo/testing

**Authorization Code (Usuarios):**
```
Flujo completo con redirecciones
```
✅ Recomendado para usuarios humanos

## 🔐 Seguridad

### Proteger el Client Secret

❌ **NO hacer:**
```javascript
// ❌ No incluir secret en código frontend
const secret = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
```

✅ **Hacer:**
```bash
# ✅ Usar variables de entorno
export KEYCLOAK_CLIENT_SECRET="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

```yaml
# ✅ En application.yml con variables
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
```

### Rotar Secrets

1. **Regenerate → Regenerate secret**
2. Copiar el nuevo secret
3. Actualizar en la aplicación
4. Desplegar
5. El secret anterior deja de funcionar inmediatamente

## 🛠️ Troubleshooting

### Error: "Invalid client credentials"

**Causa:** Client ID o Secret incorrecto

**Solución:**
1. Verificar que el `client_id` es exactamente: `m2m-service`
2. Copiar el secret de nuevo desde Keycloak
3. Verificar que no hay espacios extra

### Error: "Client not enabled"

**Causa:** El client está deshabilitado

**Solución:**
1. Ir a **Clients → m2m-service → Settings**
2. Verificar que **Enabled** está en ON
3. Click en **Save**

### Error: "Service account not enabled"

**Causa:** Service accounts roles no está habilitado

**Solución:**
1. Ir a **Clients → m2m-service → Settings**
2. Verificar que **Service accounts roles** está en ON
3. Click en **Save**

### El token no tiene roles

**Causa:** No se asignaron roles al service account

**Solución:**
1. Ir a **Clients → m2m-service → Service accounts roles**
2. Click en **Assign role**
3. Seleccionar y asignar los roles necesarios

## 📖 Recursos

### Endpoints Útiles

**OpenID Configuration:**
```
GET http://localhost:9090/realms/mi-realm/.well-known/openid-configuration
```

**Token Endpoint:**
```
POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token
```

**JWKS (Claves Públicas):**
```
GET http://localhost:9090/realms/mi-realm/protocol/openid-connect/certs
```

**UserInfo:**
```
GET http://localhost:9090/realms/mi-realm/protocol/openid-connect/userinfo
Header: Authorization: Bearer {token}
```

### Variables para Testing

```bash
# Bash
export KEYCLOAK_URL="http://localhost:9090"
export REALM="mi-realm"
export CLIENT_ID="m2m-service"
export CLIENT_SECRET="tu-secret-aqui"

# Obtener token
curl -X POST $KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET"
```

```powershell
# PowerShell
$env:KEYCLOAK_URL = "http://localhost:9090"
$env:REALM = "mi-realm"
$env:CLIENT_ID = "m2m-service"
$env:CLIENT_SECRET = "tu-secret-aqui"

# Obtener token
$body = @{
    grant_type = "client_credentials"
    client_id = $env:CLIENT_ID
    client_secret = $env:CLIENT_SECRET
}

Invoke-RestMethod -Uri "$env:KEYCLOAK_URL/realms/$env:REALM/protocol/openid-connect/token" `
  -Method Post -ContentType "application/x-www-form-urlencoded" -Body $body
```

## ✅ Checklist Final

- [ ] Client creado con ID: `m2m-service`
- [ ] Client authentication: ON
- [ ] Service accounts roles: ON
- [ ] Client secret copiado
- [ ] Roles asignados al service account (user, admin)
- [ ] Token obtenido exitosamente con curl/PowerShell
- [ ] Token decodificado en jwt.io muestra los roles correctos
- [ ] Secret guardado en lugar seguro (no en git)
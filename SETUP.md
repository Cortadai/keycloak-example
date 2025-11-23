# Configuración - Keycloak + Spring Boot (Básico)

Guía de configuración básica para la versión educativa del proyecto.

> ⚠️ **Para producción**: Ver rama **[oauth2-authorization-code](../../tree/oauth2-authorization-code)** que implementa configuración segura con variables de entorno.

## 📋 Tabla de Contenidos

1. [Instalar Keycloak](#1-instalar-keycloak)
2. [Configurar Keycloak](#2-configurar-keycloak)
3. [Configurar Spring Boot](#3-configurar-spring-boot)
4. [Verificar](#4-verificar)

---

## 1. Instalar Keycloak

### Opción A: Docker (Recomendado)

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:23.0.0 start-dev
```

### Opción B: Descarga Manual

1. Descarga desde: https://www.keycloak.org/downloads
2. Descomprime
3. Ejecuta:
   ```bash
   # Windows
   bin\kc.bat start-dev

   # Linux/Mac
   bin/kc.sh start-dev
   ```

### Verificar

```bash
curl http://localhost:8080
```

O abre: http://localhost:8080

---

## 2. Configurar Keycloak

### Paso 1: Acceder a Admin Console

1. http://localhost:8080
2. Click "Administration Console"
3. Login: `admin` / `admin`

### Paso 2: Crear Realm

1. Click dropdown superior izquierdo (donde dice "master")
2. "Create Realm"
3. **Realm name**: `mi-realm`
4. "Create"

### Paso 3: Crear Client

1. Menu lateral → "Clients"
2. "Create client"

**General Settings:**
- **Client type**: `OpenID Connect`
- **Client ID**: `spring-boot-client`
- "Next"

**Capability config:**
- **Client authentication**: `ON`
- **Authorization**: `OFF`
- **Authentication flow**: ✅ Standard flow, ✅ Direct access grants
- "Next"

**Login settings:**
- **Valid redirect URIs**: `http://localhost:8081/*`
- **Web origins**: `http://localhost:8081`
- "Save"

### Paso 4: Copiar Client Secret

1. Tab "Credentials"
2. **Copia el Client secret**
3. Guárdalo - lo usarás en `application.yml`

### Paso 5: Crear Roles

1. Menu lateral → "Realm roles"
2. "Create role"

**Rol USER:**
- **Role name**: `USER`
- "Save"

**Rol ADMIN:**
- **Role name**: `ADMIN`
- "Save"

### Paso 6: Crear Usuarios

#### Usuario Regular

1. Menu lateral → "Users"
2. "Add user"
3. **Username**: `usuario1`
4. **Email**: `usuario1@example.com`
5. **Email verified**: ON
6. "Create"

**Contraseña:**
1. Tab "Credentials"
2. "Set password"
3. **Password**: `password123`
4. **Temporary**: OFF
5. "Save" → Confirma

**Asignar rol:**
1. Tab "Role mapping"
2. "Assign role"
3. Selecciona `USER`
4. "Assign"

#### Usuario Admin

Repite con:
- **Username**: `admin1`
- **Email**: `admin1@example.com`
- **Password**: `admin123`
- **Roles**: `USER` y `ADMIN` (ambos)

---

## 3. Configurar Spring Boot

### Editar application.yml

Abre `src/main/resources/application.yml` y **pega tu client secret**:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: spring-boot-client
            client-secret: PEGA_TU_SECRET_AQUI  # ← ACTUALIZAR
```

**⚠️ IMPORTANTE**:
- Este método NO es seguro para producción
- El secret queda visible en el código
- Ver rama `oauth2-authorization-code` para método seguro

### Ejecutar

```bash
./mvnw spring-boot:run
```

---

## 4. Verificar

### Test 1: Endpoint Público

```bash
curl http://localhost:8081/public/hello
```

**Esperado:**
```json
{
  "message": "¡Hola! Este es un endpoint público."
}
```

### Test 2: Obtener Token

```bash
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_SECRET' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

**Esperado:**
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 300
}
```

### Test 3: Endpoint Protegido

```bash
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer {TOKEN}"
```

**Esperado:**
```json
{
  "username": "usuario1",
  "roles": ["ROLE_USER"],
  "authenticated": true
}
```

---

## 📋 Checklist

- [ ] Keycloak corriendo en puerto 8080
- [ ] Realm `mi-realm` creado
- [ ] Client `spring-boot-client` creado
- [ ] Client secret copiado y pegado en `application.yml`
- [ ] Roles `USER` y `ADMIN` creados
- [ ] Usuario `usuario1` creado con rol USER
- [ ] Usuario `admin1` creado con roles USER y ADMIN
- [ ] Aplicación Spring Boot inicia sin errores
- [ ] Endpoint público funciona
- [ ] Puedes obtener token
- [ ] Endpoint protegido funciona con token

---

## 🚀 Siguiente Paso

Para implementación production-ready, revisa la rama:

**[oauth2-authorization-code](../../tree/oauth2-authorization-code)**

Mejoras incluidas:
- ✅ Client secret en variables de entorno
- ✅ OAuth2 Authorization Code Flow
- ✅ Configuración simplificada con `issuer-uri`
- ✅ Mappers configurados
- ✅ Dual authentication

---

Ver ejemplos de uso completos en **[USAGE.md](USAGE.md)**

# Configuración - Keycloak + Spring Boot (Básico)

Guía paso a paso para configurar Keycloak desde cero y conectarlo con Spring Boot.

> **Rama:** `main` - Versión educativa básica
> **Nivel:** 🌱 Principiante

---

## 📋 Tabla de Contenidos

1. [Instalar Keycloak](#1-instalar-keycloak)
2. [Configurar Keycloak](#2-configurar-keycloak)
3. [Configurar Spring Boot](#3-configurar-spring-boot)
4. [Verificar](#4-verificar)

---

## 1. Instalar Keycloak

### Docker (Recomendado)

```bash
docker run -p 9090:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

**Nota:** Mapeamos puerto `9090` del host → `8080` del contenedor.

### Verificar

```bash
curl http://localhost:9090
```

O abre en navegador: http://localhost:9090

---

## 2. Configurar Keycloak

### Paso 1: Acceder a Admin Console

1. Abre: http://localhost:9090/admin
2. Login: `admin` / `admin`

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
- **Authentication flow**:
  - ✅ Standard flow
  - ✅ Direct access grants
- "Next"

**Login settings:**
- **Valid redirect URIs**: `http://localhost:8081/*`
- **Web origins**: `http://localhost:8081`
- "Save"

### Paso 4: Copiar Client Secret

1. Tab "Credentials"
2. **Copia el Client secret**
3. Lo necesitarás para obtener tokens (pero NO lo guardamos en `application.yml` en esta versión básica)

### Paso 5: Crear Roles

1. Menu lateral → "Realm roles"
2. "Create role"

**Rol user:**
- **Role name**: `user`
- "Save"

**Rol admin:**
- **Role name**: `admin`
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
3. Selecciona `user`
4. "Assign"

#### Usuario Admin

Repite con:
- **Username**: `admin1`
- **Email**: `admin1@example.com`
- **Password**: `admin123`
- **Roles**: `user` y `admin` (ambos)

---

## 3. Configurar Spring Boot

### ¿Qué configurar?

**¡Nada!** El archivo `application.yml` ya está configurado:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/mi-realm
```

**Eso es todo.** Spring Boot obtiene automáticamente:
- Las claves públicas para validar tokens
- La configuración de OpenID Connect
- Todo lo necesario

### Ejecutar

```bash
./mvnw spring-boot:run
```

La API estará en: http://localhost:8081

---

## 4. Verificar

### Test 1: Endpoint Público

```bash
curl http://localhost:8081/public/hello
```

**Esperado:**
```json
{
  "message": "¡Hola! Este es un endpoint público.",
  "info": "No necesitas estar autenticado para ver esto."
}
```

### Test 2: Obtener Token

```bash
curl -X POST 'http://localhost:9090/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_SECRET' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

**Reemplaza `TU_SECRET`** con el client secret que copiaste en el Paso 4.

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
# Primero guarda el token
export TOKEN="eyJhbGc..."

# Luego prueba el endpoint
curl http://localhost:8081/api/user/me \
  -H "Authorization: Bearer $TOKEN"
```

**Esperado:**
```json
{
  "username": "usuario1",
  "email": "usuario1@example.com",
  "roles": ["ROLE_USER"],
  "authenticated": true
}
```

---

## 📋 Checklist

- [ ] Keycloak corriendo en puerto 9090
- [ ] Realm `mi-realm` creado
- [ ] Client `spring-boot-client` creado
- [ ] Client secret copiado (para usar en los tests)
- [ ] Roles `user` y `admin` creados
- [ ] Usuario `usuario1` creado con rol user
- [ ] Usuario `admin1` creado con roles user y admin
- [ ] Aplicación Spring Boot inicia sin errores
- [ ] Endpoint público funciona
- [ ] Puedes obtener token
- [ ] Endpoint protegido funciona con token

---

## 🎯 ¿Qué Aprendiste?

Con esta configuración básica ahora entiendes:

✅ Cómo levantar Keycloak con Docker
✅ Estructura básica: Realm → Client → Roles → Usuarios
✅ Cómo Spring Boot valida tokens automáticamente con `issuer-uri`
✅ Diferencia entre endpoints públicos y protegidos
✅ Cómo obtener un token JWT desde Keycloak
✅ Arquitectura STATELESS (cada request incluye el token)

---

## 🚀 Siguiente Paso

Una vez que domines esta configuración básica, continúa con:

### Rama `oauth2-resource-server`

**Qué añade:**
- Client Credentials (M2M)
- Service Accounts
- Comunicación servicio-a-servicio

**Cuándo usarla:**
- APIs backend que se comunican entre sí
- Microservicios
- Cron jobs que llaman APIs

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
- Authorization Code Flow completo
- Patrón BFF (Backend for Frontend)
- Cookies HttpOnly
- STATEFUL (sesiones)
- Integración con SPAs (React, Angular)

**Cuándo usarla:**
- Aplicaciones web modernas (SPAs)
- Máxima seguridad para frontend

```bash
git checkout oauth2-bff-cookies
```

---

Ver ejemplos de uso completos en **[USAGE.md](USAGE.md)**

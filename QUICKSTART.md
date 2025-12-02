# Guía de Inicio Rápido - OAuth2 BFF con Headers y Redis

Esta guía te llevará paso a paso desde cero hasta tener la aplicación funcionando completamente.

---

## Índice

1. [Requisitos Previos](#1-requisitos-previos)
2. [Levantar Infraestructura](#2-levantar-infraestructura)
3. [Configurar Keycloak](#3-configurar-keycloak)
4. [Configurar el Backend](#4-configurar-el-backend)
5. [Ejecutar el Backend](#5-ejecutar-el-backend)
6. [Ejecutar el Frontend](#6-ejecutar-el-frontend)
7. [Probar el Flujo Completo](#7-probar-el-flujo-completo)
8. [Verificar Redis](#8-verificar-redis)
9. [Probar el Refresh de Tokens](#9-probar-el-refresh-de-tokens)
10. [Probar el Logout](#10-probar-el-logout)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Requisitos Previos

Asegúrate de tener instalado:

- **Java 17+**: `java -version`
- **Maven 3.6+**: `mvn -version`
- **Node.js 18+**: `node -version`
- **npm 9+**: `npm -version`
- **Docker**: `docker --version`
- **Docker Compose**: `docker-compose --version`

---

## 2. Levantar Infraestructura

Desde la raíz del proyecto, ejecuta:

```bash
docker-compose up -d
```

Esto levanta:
- **Keycloak** en `http://localhost:9090`
- **Redis** en `localhost:6379`

### Verificar que están corriendo:

```bash
docker-compose ps
```

Deberías ver algo como:

```
NAME       IMAGE                              STATUS          PORTS
keycloak   quay.io/keycloak/keycloak:latest   Up 2 minutes    0.0.0.0:9090->8080/tcp
redis      redis:7-alpine                     Up 2 minutes    0.0.0.0:6379->6379/tcp
```

### Esperar a que Keycloak esté listo:

Keycloak tarda ~60 segundos en arrancar. Verifica accediendo a:

```
http://localhost:9090
```

Deberías ver la página de bienvenida de Keycloak.

---

## 3. Configurar Keycloak

### 3.1. Acceder a la Consola de Administración

1. Abre `http://localhost:9090`
2. Click en **Administration Console**
3. Login con:
   - **Username**: `admin`
   - **Password**: `admin`

### 3.2. Crear un Realm

1. En el menú desplegable superior izquierdo (donde dice "master"), click en **Create realm**
2. Configurar:
   - **Realm name**: `mi-realm`
3. Click en **Create**

![Crear Realm](https://i.imgur.com/placeholder.png)

### 3.3. Crear un Client

1. En el menú lateral, ve a **Clients**
2. Click en **Create client**
3. **General Settings**:
   - **Client type**: OpenID Connect
   - **Client ID**: `spring-boot-client`
   - Click **Next**

4. **Capability config**:
   - **Client authentication**: `ON` (esto lo hace confidential)
   - **Authorization**: `OFF`
   - **Authentication flow**: Marca solo **Standard flow** (desmarca Direct access grants)
   - Click **Next**

5. **Login settings**:
   - **Root URL**: `http://localhost:8081`
   - **Home URL**: `http://localhost:8081`
   - **Valid redirect URIs**: `http://localhost:8081/*`
   - **Valid post logout redirect URIs**: `http://localhost:4200/*`
   - **Web origins**: `http://localhost:4200`
   - Click **Save**

### 3.4. Obtener el Client Secret

1. En la página del client `spring-boot-client`, ve a la pestaña **Credentials**
2. Copia el valor de **Client secret**
3. **Guárdalo**, lo necesitarás en el siguiente paso

Ejemplo: `abc123def456ghi789...`

### 3.5. Crear Roles

1. En el menú lateral, ve a **Realm roles**
2. Click en **Create role**
3. Crear rol `user`:
   - **Role name**: `user`
   - Click **Save**
4. Volver a **Realm roles** y crear rol `admin`:
   - **Role name**: `admin`
   - Click **Save**

### 3.6. Crear un Usuario de Prueba

1. En el menú lateral, ve a **Users**
2. Click en **Add user**
3. Configurar:
   - **Username**: `testuser`
   - **Email**: `test@example.com`
   - **Email verified**: `ON`
   - **First name**: `Test`
   - **Last name**: `User`
   - Click **Create**

4. **Establecer contraseña**:
   - Ve a la pestaña **Credentials**
   - Click en **Set password**
   - **Password**: `test123`
   - **Password confirmation**: `test123`
   - **Temporary**: `OFF`
   - Click **Save** y confirma

5. **Asignar roles**:
   - Ve a la pestaña **Role mapping**
   - Click en **Assign role**
   - Selecciona `user` y `admin` (o solo `user` si quieres probar permisos)
   - Click **Assign**

### 3.7. (Opcional) Crear Usuario Solo con Rol USER

Repite el paso 3.6 pero:
- **Username**: `normaluser`
- **Password**: `normal123`
- Solo asigna el rol `user`

---

## 4. Configurar el Backend

### 4.1. Actualizar el Client Secret

Edita el archivo `src/main/resources/application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-secret: TU_CLIENT_SECRET_AQUI  # <-- Pega aquí el secret copiado
```

Reemplaza `TU_CLIENT_SECRET_AQUI` con el Client Secret que copiaste en el paso 3.4.

### 4.2. Verificar Configuración de Redis

El archivo `application.yml` ya está configurado para Redis en localhost:6379. No necesitas cambiar nada si seguiste los pasos anteriores.

---

## 5. Ejecutar el Backend

Desde la raíz del proyecto:

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### Verificar que está corriendo:

Abre en el navegador o con curl:

```bash
curl http://localhost:8081/public/status
```

Deberías recibir:

```json
{
  "status": "UP",
  "message": "El servidor está funcionando correctamente"
}
```

### Ver logs:

El backend mostrará logs de Spring Security. Busca:

```
Started KeycloakDemoApplication in X.XXX seconds
```

---

## 6. Ejecutar el Frontend

En una **nueva terminal**, ve al directorio frontend:

```bash
cd frontend
npm install
npm start
```

### Verificar que está corriendo:

Abre en el navegador:

```
http://localhost:4200
```

Deberías ver la página de login con el botón "Login con Keycloak".

---

## 7. Probar el Flujo Completo

### 7.1. Iniciar Login

1. Abre `http://localhost:4200` en el navegador
2. Abre las **DevTools** (F12) → pestaña **Network** (para ver las peticiones)
3. Click en **"Login con Keycloak"**

### 7.2. Autenticarse en Keycloak

1. Serás redirigido a la página de login de Keycloak (`localhost:9090/...`)
2. Introduce las credenciales:
   - **Username**: `testuser`
   - **Password**: `test123`
3. Click en **Sign In**

### 7.3. Verificar Callback

1. Serás redirigido a `http://localhost:4200/callback?code=xxx`
2. Verás brevemente un spinner con "Completando autenticación..."
3. Luego serás redirigido automáticamente a `/dashboard`

### 7.4. Verificar Dashboard

En el dashboard deberías ver:
- Tu nombre de usuario
- Tu email
- Tus roles (USER, ADMIN)
- Información de seguridad BFF

### 7.5. Verificar Token en localStorage

1. En DevTools, ve a **Application** → **Local Storage** → `http://localhost:4200`
2. Deberías ver:
   - `access_token`: El JWT (una cadena larga)
   - `token_expiry`: Timestamp de expiración (número)

### 7.6. Verificar Header Authorization

1. En DevTools → **Network**, filtra por `user`
2. Click en la petición a `/api/user/me`
3. En **Headers** → **Request Headers**, verifica:
   ```
   Authorization: Bearer eyJhbGc...
   ```

---

## 8. Verificar Redis

### 8.1. Conectar a Redis CLI

```bash
docker exec -it redis redis-cli
```

### 8.2. Ver todas las claves

```bash
KEYS *
```

Deberías ver algo como:

```
1) "refresh_token:a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

El UUID es el `sub` claim del usuario en Keycloak.

### 8.3. Ver el refresh token

```bash
GET "refresh_token:TU_USER_ID"
```

Verás el refresh token almacenado (una cadena JWT).

### 8.4. Ver TTL del refresh token

```bash
TTL "refresh_token:TU_USER_ID"
```

Debería mostrar un número cercano a 28800 (8 horas en segundos).

### 8.5. Salir de Redis CLI

```bash
exit
```

---

## 9. Probar el Refresh de Tokens

### Opción A: Esperar a que expire (no recomendado para pruebas)

El token expira en ~5-15 minutos según la configuración de Keycloak.

### Opción B: Forzar refresh modificando el timestamp

1. En DevTools → **Application** → **Local Storage**
2. Edita `token_expiry` y pon un valor en el pasado: `1000`
3. Recarga la página o haz una petición

### Opción C: Observar el refresh proactivo

1. Abre la **Console** en DevTools
2. Espera unos minutos (el refresh proactivo se programa 2 minutos antes de expirar)
3. Verás en consola:
   ```
   Refresh programado en X minutos
   Refresh proactivo del token...
   Token refrescado proactivamente
   ```

### Opción D: Simular 401 desde el backend

1. En Redis CLI, elimina el refresh token:
   ```bash
   DEL "refresh_token:TU_USER_ID"
   ```
2. Modifica `token_expiry` en localStorage para forzar expiración
3. Haz una petición - debería redirigir a login

---

## 10. Probar el Logout

### 10.1. Hacer Logout

1. En el dashboard, click en **"Logout"**
2. Serás redirigido a la página de login

### 10.2. Verificar localStorage vacío

1. En DevTools → **Application** → **Local Storage**
2. No debería haber `access_token` ni `token_expiry`

### 10.3. Verificar Redis vacío

```bash
docker exec -it redis redis-cli KEYS "*"
```

El refresh token del usuario debería haber sido eliminado.

### 10.4. Verificar que no puedes acceder al dashboard

1. Intenta navegar manualmente a `http://localhost:4200/dashboard`
2. Deberías ser redirigido a `/login`

---

## 11. Troubleshooting

### Error: "Invalid redirect URI"

**Causa**: Las URIs configuradas en Keycloak no coinciden.

**Solución**:
1. Ve a Keycloak → Clients → spring-boot-client → Settings
2. Verifica:
   - Valid redirect URIs: `http://localhost:8081/*`
   - Web origins: `http://localhost:4200`

### Error: "CORS error" en el navegador

**Causa**: El backend no está corriendo o CORS mal configurado.

**Solución**:
1. Verifica que el backend esté corriendo en puerto 8081
2. Reinicia el backend: `mvn spring-boot:run`

### Error: "Código temporal inválido o expirado"

**Causa**: El código temporal tiene TTL de 30 segundos.

**Solución**:
1. Verifica que Redis esté corriendo: `docker-compose ps`
2. Intenta el login de nuevo (más rápido esta vez)
3. Revisa logs del backend para más detalles

### Error: "Connection refused" a Redis

**Causa**: Redis no está corriendo.

**Solución**:
```bash
docker-compose up -d redis
docker-compose ps
```

### Error: 401 en todas las peticiones

**Causa**: Token inválido o expirado.

**Solución**:
1. Limpia localStorage: DevTools → Application → Local Storage → Clear All
2. Haz login de nuevo

### El login redirige pero no llega al dashboard

**Causa**: Error en el callback o exchange.

**Solución**:
1. Abre DevTools → Console y busca errores
2. Revisa Network para ver si `/api/auth/exchange` falla
3. Revisa logs del backend

### Keycloak no arranca

**Causa**: Puerto ocupado o falta de recursos.

**Solución**:
```bash
docker-compose down
docker-compose up -d
docker-compose logs keycloak
```

---

## Comandos Útiles

### Docker

```bash
# Ver logs de Keycloak
docker-compose logs -f keycloak

# Ver logs de Redis
docker-compose logs -f redis

# Reiniciar todo
docker-compose restart

# Parar todo
docker-compose down

# Parar y eliminar volúmenes (reset completo)
docker-compose down -v
```

### Redis

```bash
# Conectar a Redis
docker exec -it redis redis-cli

# Ver todas las claves
KEYS *

# Ver una clave específica
GET "nombre_clave"

# Ver TTL
TTL "nombre_clave"

# Eliminar una clave
DEL "nombre_clave"

# Limpiar todo Redis
FLUSHALL
```

### Backend

```bash
# Compilar sin tests
mvn clean install -DskipTests

# Ejecutar
mvn spring-boot:run

# Ejecutar tests
mvn test
```

### Frontend

```bash
# Instalar dependencias
npm install

# Ejecutar en desarrollo
npm start

# Build producción
npm run build

# Ejecutar tests
npm test
```

---

## Próximos Pasos

Una vez que todo funcione:

1. **Prueba con diferentes usuarios** (con y sin rol ADMIN)
2. **Prueba los endpoints de admin** (`/api/admin/*`) con un usuario sin rol ADMIN
3. **Prueba el refresh automático** esperando a que el token expire
4. **Prueba múltiples pestañas** - nuevo login debería invalidar el anterior
5. **Revisa la consola del navegador** para ver los logs de refresh proactivo

---

## Resumen de URLs

| Servicio | URL |
|----------|-----|
| Frontend Angular | http://localhost:4200 |
| Backend Spring Boot | http://localhost:8081 |
| Keycloak Admin | http://localhost:9090 |
| Redis | localhost:6379 |

---

¡Listo! Si seguiste todos los pasos, deberías tener el sistema OAuth2 BFF con Headers funcionando completamente.

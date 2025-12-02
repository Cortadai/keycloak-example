# Guia de Inicio Rapido - OAuth2 BFF con Binding (Llave Partida)

Esta guia te llevara paso a paso desde cero hasta tener la aplicacion funcionando completamente.

---

## Indice

1. [Requisitos Previos](#1-requisitos-previos)
2. [Levantar Infraestructura](#2-levantar-infraestructura)
3. [Configurar Keycloak](#3-configurar-keycloak)
4. [Configurar el Backend](#4-configurar-el-backend)
5. [Ejecutar el Backend](#5-ejecutar-el-backend)
6. [Ejecutar el Frontend](#6-ejecutar-el-frontend)
7. [Probar el Flujo Completo](#7-probar-el-flujo-completo)
8. [Verificar Redis](#8-verificar-redis)
9. [Verificar el Binding (Llave Partida)](#9-verificar-el-binding-llave-partida)
10. [Probar el Refresh de Tokens](#10-probar-el-refresh-de-tokens)
11. [Probar el Logout](#11-probar-el-logout)
12. [Troubleshooting](#12-troubleshooting)

---

## Arquitectura de Seguridad

Esta rama implementa el patron **Binding (Llave Partida)**:

```
┌─────────────────────────────────────────────────────────────┐
│  FRONTEND (Angular)                                         │
│  ┌────────────────────┐    ┌────────────────────────────┐  │
│  │ localStorage       │    │ Cookie (automatica)         │  │
│  │ access_token (JWT) │    │ fingerprint_hash (HttpOnly) │  │
│  │ con claim          │    │ SHA-256(fingerprint)        │  │
│  │ "fingerprint"      │    │                             │  │
│  └────────────────────┘    └────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PETICION HTTP                                              │
│  Header: Authorization: Bearer <JWT con fingerprint>        │
│  Cookie: fingerprint_hash=<SHA-256(fingerprint)>            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  BACKEND (Spring Boot)                                      │
│  FingerprintValidationFilter:                               │
│  1. Extrae fingerprint del JWT                              │
│  2. Calcula SHA-256(fingerprint)                            │
│  3. Compara con cookie fingerprint_hash                     │
│  4. Si NO coinciden → 401 Unauthorized                      │
└─────────────────────────────────────────────────────────────┘
```

**Proteccion:**
- **XSS**: Atacante roba JWT de localStorage → NO tiene cookie HttpOnly → BLOQUEADO
- **CSRF**: Atacante envia cookie automatica → NO puede leer JWT → BLOQUEADO

---

## 1. Requisitos Previos

Asegurate de tener instalado:

- **Java 17+**: `java -version`
- **Maven 3.6+**: `mvn -version`
- **Node.js 18+**: `node -version`
- **npm 9+**: `npm -version`
- **Docker**: `docker --version`
- **Docker Compose**: `docker-compose --version`

---

## 2. Levantar Infraestructura

Desde la raiz del proyecto, ejecuta:

```bash
docker-compose up -d
```

Esto levanta:
- **Keycloak** en `http://localhost:9090`
- **Redis** en `localhost:6379`

### Verificar que estan corriendo:

```bash
docker-compose ps
```

Deberias ver algo como:

```
NAME       IMAGE                              STATUS          PORTS
keycloak   quay.io/keycloak/keycloak:latest   Up 2 minutes    0.0.0.0:9090->8080/tcp
redis      redis:7-alpine                     Up 2 minutes    0.0.0.0:6379->6379/tcp
```

### Esperar a que Keycloak este listo:

Keycloak tarda ~60 segundos en arrancar. Verifica accediendo a:

```
http://localhost:9090
```

Deberias ver la pagina de bienvenida de Keycloak.

---

## 3. Configurar Keycloak

### 3.1. Acceder a la Consola de Administracion

1. Abre `http://localhost:9090`
2. Click en **Administration Console**
3. Login con:
   - **Username**: `admin`
   - **Password**: `admin`

### 3.2. Crear un Realm

1. En el menu desplegable superior izquierdo (donde dice "master"), click en **Create realm**
2. Configurar:
   - **Realm name**: `mi-realm`
3. Click en **Create**

### 3.3. Crear un Client

1. En el menu lateral, ve a **Clients**
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

1. En la pagina del client `spring-boot-client`, ve a la pestana **Credentials**
2. Copia el valor de **Client secret**
3. **Guardalo**, lo necesitaras en el siguiente paso

Ejemplo: `abc123def456ghi789...`

### 3.5. Crear Roles

1. En el menu lateral, ve a **Realm roles**
2. Click en **Create role**
3. Crear rol `user`:
   - **Role name**: `user`
   - Click **Save**
4. Volver a **Realm roles** y crear rol `admin`:
   - **Role name**: `admin`
   - Click **Save**

### 3.6. Crear un Usuario de Prueba

1. En el menu lateral, ve a **Users**
2. Click en **Add user**
3. Configurar:
   - **Username**: `testuser`
   - **Email**: `test@example.com`
   - **Email verified**: `ON`
   - **First name**: `Test`
   - **Last name**: `User`
   - Click **Create**

4. **Establecer contrasena**:
   - Ve a la pestana **Credentials**
   - Click en **Set password**
   - **Password**: `test123`
   - **Password confirmation**: `test123`
   - **Temporary**: `OFF`
   - Click **Save** y confirma

5. **Asignar roles**:
   - Ve a la pestana **Role mapping**
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
            client-secret: TU_CLIENT_SECRET_AQUI  # <-- Pega aqui el secret copiado
```

Reemplaza `TU_CLIENT_SECRET_AQUI` con el Client Secret que copiaste en el paso 3.4.

### 4.2. Verificar Configuracion de Redis

El archivo `application.yml` ya esta configurado para Redis en localhost:6379. No necesitas cambiar nada si seguiste los pasos anteriores.

### 4.3. Configuracion del Binding (ya incluida)

El archivo `application.yml` ya incluye la configuracion para el patron Binding:

```yaml
jwt:
  secret: mi-secret-super-seguro-para-jwt-binding  # Para produccion: usar variable de entorno
  expiration: 900000  # 15 minutos

fingerprint:
  cookie-name: fingerprint_hash
  max-age: 86400  # 24 horas
  secure: false   # Para produccion con HTTPS: true
  same-site: Lax
```

---

## 5. Ejecutar el Backend

Desde la raiz del proyecto:

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

### Verificar que esta corriendo:

Abre en el navegador o con curl:

```bash
curl http://localhost:8081/public/status
```

Deberias recibir:

```json
{
  "status": "UP",
  "message": "El servidor esta funcionando correctamente"
}
```

### Ver logs:

El backend mostrara logs de Spring Security. Busca:

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

### Verificar que esta corriendo:

Abre en el navegador:

```
http://localhost:4200
```

Deberias ver la pagina de login con el boton "Login con Keycloak" y la nota de seguridad "Llave Partida".

---

## 7. Probar el Flujo Completo

### 7.1. Iniciar Login

1. Abre `http://localhost:4200` en el navegador
2. Abre las **DevTools** (F12) → pestana **Network** (para ver las peticiones)
3. Click en **"Login con Keycloak"**

### 7.2. Autenticarse en Keycloak

1. Seras redirigido a la pagina de login de Keycloak (`localhost:9090/...`)
2. Introduce las credenciales:
   - **Username**: `testuser`
   - **Password**: `test123`
3. Click en **Sign In**

### 7.3. Verificar Callback

1. Seras redirigido a `http://localhost:4200/callback?code=xxx`
2. Veras brevemente un spinner con "Completando autenticacion..."
3. Luego seras redirigido automaticamente a `/dashboard`

### 7.4. Verificar Dashboard

En el dashboard deberias ver:
- Tu nombre de usuario
- Tu email
- Tus roles (USER, ADMIN)
- Informacion de seguridad BFF con Binding

### 7.5. Verificar JWT en localStorage

1. En DevTools, ve a **Application** → **Local Storage** → `http://localhost:4200`
2. Deberias ver:
   - `access_token`: El JWT con claim `fingerprint` (una cadena larga)
   - `token_expiry`: Timestamp de expiracion (numero)

### 7.6. Verificar Cookie HttpOnly

1. En DevTools → **Application** → **Cookies** → `http://localhost:4200`
2. Deberias ver:
   - `fingerprint_hash`: Hash SHA-256 del fingerprint
   - Flags: `HttpOnly`, `SameSite=Lax`

**Nota**: La cookie NO es visible desde JavaScript (`document.cookie`) porque es HttpOnly.

### 7.7. Verificar Header Authorization

1. En DevTools → **Network**, filtra por `user`
2. Click en la peticion a `/api/user/me`
3. En **Headers** → **Request Headers**, verifica:
   ```
   Authorization: Bearer eyJhbGc...
   ```
4. En **Request Headers**, tambien veras que la cookie se envia automaticamente

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

Deberias ver algo como:

```
1) "refresh_token:a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

El UUID es el `sub` claim del usuario en Keycloak.

### 8.3. Ver el refresh token

```bash
GET "refresh_token:TU_USER_ID"
```

Veras el refresh token de Keycloak almacenado (una cadena JWT).

### 8.4. Ver TTL del refresh token

```bash
TTL "refresh_token:TU_USER_ID"
```

Deberia mostrar un numero cercano a 28800 (8 horas en segundos).

### 8.5. Salir de Redis CLI

```bash
exit
```

---

## 9. Verificar el Binding (Llave Partida)

### 9.1. Prueba de Seguridad: Sin Cookie

Para verificar que el binding funciona, prueba hacer una peticion SIN la cookie:

```bash
# Obtener el token de localStorage (copialo de DevTools)
TOKEN="eyJhbGc..."

# Peticion sin cookie - deberia fallar con 401
curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/user/me
```

Resultado esperado: `401 Unauthorized` - porque falta la cookie.

### 9.2. Prueba de Seguridad: Cookie Incorrecta

```bash
# Peticion con cookie incorrecta - deberia fallar con 401
curl -H "Authorization: Bearer $TOKEN" \
     -H "Cookie: fingerprint_hash=hash_incorrecto" \
     http://localhost:8081/api/user/me
```

Resultado esperado: `401 Unauthorized` - porque el hash no coincide.

### 9.3. Verificar en Logs del Backend

En los logs del backend, veras:

```
FingerprintValidationFilter - Fingerprint validation successful for user: testuser
```

O si falla:

```
FingerprintValidationFilter - Fingerprint hash mismatch
```

---

## 10. Probar el Refresh de Tokens

### Opcion A: Esperar a que expire (no recomendado para pruebas)

El token expira en ~15 minutos segun la configuracion.

### Opcion B: Forzar refresh modificando el timestamp

1. En DevTools → **Application** → **Local Storage**
2. Edita `token_expiry` y pon un valor en el pasado: `1000`
3. Recarga la pagina o haz una peticion

### Opcion C: Observar el refresh proactivo

1. Abre la **Console** en DevTools
2. Espera unos minutos (el refresh proactivo se programa 2 minutos antes de expirar)
3. Veras en consola:
   ```
   Refresh programado en X minutos
   Refresh proactivo del token...
   Token refrescado proactivamente
   ```

**Importante**: En cada refresh, el fingerprint se ROTA:
- Nuevo JWT con nuevo claim `fingerprint`
- Nueva cookie con nuevo hash SHA-256

### Opcion D: Simular 401 desde el backend

1. En Redis CLI, elimina el refresh token:
   ```bash
   DEL "refresh_token:TU_USER_ID"
   ```
2. Modifica `token_expiry` en localStorage para forzar expiracion
3. Haz una peticion - deberia redirigir a login

---

## 11. Probar el Logout

### 11.1. Hacer Logout

1. En el dashboard, click en **"Logout"**
2. Seras redirigido a la pagina de login

### 11.2. Verificar localStorage vacio

1. En DevTools → **Application** → **Local Storage**
2. No deberia haber `access_token` ni `token_expiry`

### 11.3. Verificar Cookie eliminada

1. En DevTools → **Application** → **Cookies**
2. No deberia haber `fingerprint_hash`

### 11.4. Verificar Redis vacio

```bash
docker exec -it redis redis-cli KEYS "*"
```

El refresh token del usuario deberia haber sido eliminado.

### 11.5. Verificar que no puedes acceder al dashboard

1. Intenta navegar manualmente a `http://localhost:4200/dashboard`
2. Deberias ser redirigido a `/login`

---

## 12. Troubleshooting

### Error: "Invalid redirect URI"

**Causa**: Las URIs configuradas en Keycloak no coinciden.

**Solucion**:
1. Ve a Keycloak → Clients → spring-boot-client → Settings
2. Verifica:
   - Valid redirect URIs: `http://localhost:8081/*`
   - Web origins: `http://localhost:4200`

### Error: "CORS error" en el navegador

**Causa**: El backend no esta corriendo o CORS mal configurado.

**Solucion**:
1. Verifica que el backend este corriendo en puerto 8081
2. Verifica que CORS permite credentials:
   ```yaml
   cors:
     allowed-origins: http://localhost:4200
     allow-credentials: true
   ```
3. Reinicia el backend: `mvn spring-boot:run`

### Error: "Codigo temporal invalido o expirado"

**Causa**: El codigo temporal tiene TTL de 30 segundos.

**Solucion**:
1. Verifica que Redis este corriendo: `docker-compose ps`
2. Intenta el login de nuevo (mas rapido esta vez)
3. Revisa logs del backend para mas detalles

### Error: "Connection refused" a Redis

**Causa**: Redis no esta corriendo.

**Solucion**:
```bash
docker-compose up -d redis
docker-compose ps
```

### Error: 401 en todas las peticiones

**Causa**: Token invalido, expirado, o binding fallido.

**Solucion**:
1. Verifica que tienes AMBOS:
   - JWT en localStorage (`access_token`)
   - Cookie HttpOnly (`fingerprint_hash`)
2. Limpia localStorage: DevTools → Application → Local Storage → Clear All
3. Limpia cookies: DevTools → Application → Cookies → Clear All
4. Haz login de nuevo

### Error: "Fingerprint hash mismatch" en logs

**Causa**: El hash de la cookie no coincide con SHA-256(fingerprint del JWT).

**Solucion**:
1. Esto puede pasar si el JWT fue manipulado o la cookie fue modificada
2. Haz logout y login de nuevo

### El login redirige pero no llega al dashboard

**Causa**: Error en el callback o exchange.

**Solucion**:
1. Abre DevTools → Console y busca errores
2. Revisa Network para ver si `/api/auth/exchange` falla
3. Revisa logs del backend

### Cookie no se envia (withCredentials)

**Causa**: El frontend no esta enviando credentials.

**Solucion**:
1. Verifica que el interceptor tiene `withCredentials: true`
2. Verifica que CORS tiene `allow-credentials: true`
3. Verifica que las URLs son correctas (mismo dominio o CORS configurado)

### Keycloak no arranca

**Causa**: Puerto ocupado o falta de recursos.

**Solucion**:
```bash
docker-compose down
docker-compose up -d
docker-compose logs keycloak
```

---

## Comandos Utiles

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

# Parar y eliminar volumenes (reset completo)
docker-compose down -v
```

### Redis

```bash
# Conectar a Redis
docker exec -it redis redis-cli

# Ver todas las claves
KEYS *

# Ver una clave especifica
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

# Build produccion
npm run build

# Ejecutar tests
npm test
```

---

## Proximos Pasos

Una vez que todo funcione:

1. **Prueba con diferentes usuarios** (con y sin rol ADMIN)
2. **Prueba los endpoints de admin** (`/api/admin/*`) con un usuario sin rol ADMIN
3. **Prueba el refresh automatico** esperando a que el token expire - observa la rotacion del fingerprint
4. **Prueba multiples pestanas** - nuevo login deberia invalidar el anterior
5. **Prueba la seguridad del binding** - intenta hacer peticiones sin cookie o con JWT robado
6. **Revisa la consola del navegador** para ver los logs de refresh proactivo

---

## Resumen de URLs

| Servicio | URL |
|----------|-----|
| Frontend Angular | http://localhost:4200 |
| Backend Spring Boot | http://localhost:8081 |
| Keycloak Admin | http://localhost:9090 |
| Redis | localhost:6379 |

---

## Comparacion con Otras Ramas

| Aspecto | cookies | headers | **binding** |
|---------|---------|---------|-------------|
| JWT en | Cookie HttpOnly | localStorage | **localStorage** |
| Viaja como | Cookie automatica | Header Authorization | **Header + Cookie** |
| Cookie adicional | No | No | **fingerprint_hash** |
| Proteccion XSS | Total | Vulnerable | **Binding** |
| Proteccion CSRF | SameSite | Total | **Total** |
| Complejidad | Baja | Media | **Alta** |

---

¡Listo! Si seguiste todos los pasos, deberias tener el sistema OAuth2 BFF con Binding (Llave Partida) funcionando completamente.

# Configuración de Keycloak - Guía Paso a Paso

Esta guía te enseñará cómo configurar Keycloak desde cero para que funcione con la aplicación Spring Boot.

## Paso 1: Instalar y Ejecutar Keycloak

### Opción A: Usando Docker (Recomendado)

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

### Opción B: Descarga Manual

1. Descarga Keycloak desde: https://www.keycloak.org/downloads
2. Descomprime el archivo
3. Ejecuta:
   ```bash
   # Windows
   bin\kc.bat start-dev

   # Linux/Mac
   bin/kc.sh start-dev
   ```

### Verificar que Keycloak está funcionando

- Abre tu navegador en: http://localhost:8080
- Deberías ver la página de bienvenida de Keycloak

## Paso 2: Acceder a la Consola de Administración

1. Ve a: http://localhost:8080
2. Haz clic en "Administration Console"
3. Ingresa las credenciales:
   - **Username**: admin
   - **Password**: admin

## Paso 3: Crear un Realm

Un **Realm** es un espacio de trabajo aislado para tus usuarios y aplicaciones.

1. En la consola de administración, ve al menú desplegable en la esquina superior izquierda (donde dice "Keycloak" o "master")
2. Haz clic en **"Create Realm"**
3. Ingresa los siguientes datos:
   - **Realm name**: `mi-realm`
4. Haz clic en **"Create"**

**Importante**: Asegúrate de que el nombre del realm coincida con el que configuraste en `application.yml`

## Paso 4: Crear un Client (Cliente)

Un **Client** representa tu aplicación Spring Boot.

1. En el menú lateral izquierdo, haz clic en **"Clients"**
2. Haz clic en **"Create client"**
3. En la pestaña **"General Settings"**:
   - **Client type**: OpenID Connect
   - **Client ID**: `spring-boot-client`
   - Haz clic en **"Next"**

4. En la pestaña **"Capability config"**:
   - **Client authentication**: ON (esto lo hace "confidential")
   - **Authorization**: OFF (no lo necesitamos para este ejemplo)
   - **Authentication flow**: Marca estas opciones:
     - ✅ Standard flow
     - ✅ Direct access grants
   - Haz clic en **"Next"**

5. En la pestaña **"Login settings"**:
   - **Root URL**: `http://localhost:8081`
   - **Home URL**: `http://localhost:8081`
   - **Valid redirect URIs**: `http://localhost:8081/*`
   - **Valid post logout redirect URIs**: `http://localhost:8081/*`
   - **Web origins**: `http://localhost:8081`
   - Haz clic en **"Save"**

6. Ve a la pestaña **"Credentials"**:
   - Copia el **Client secret**
   - Pégalo en `application.yml` en la propiedad `client-secret`

## Paso 5: Crear Roles

Los **Roles** definen qué pueden hacer los usuarios.

### Crear roles del Realm:

1. En el menú lateral, haz clic en **"Realm roles"**
2. Haz clic en **"Create role"**
3. Crea los siguientes roles (uno por uno):

   **Role 1:**
   - **Role name**: `USER`
   - **Description**: Usuario regular
   - Haz clic en **"Save"**

   **Role 2:**
   - **Role name**: `ADMIN`
   - **Description**: Administrador
   - Haz clic en **"Save"**

## Paso 6: Crear Usuarios

Ahora crearemos usuarios de prueba.

### Usuario 1: Usuario Regular

1. En el menú lateral, haz clic en **"Users"**
2. Haz clic en **"Add user"**
3. Completa el formulario:
   - **Username**: `usuario1`
   - **Email**: `usuario1@example.com`
   - **First name**: Juan
   - **Last name**: Pérez
   - **Email verified**: ON
4. Haz clic en **"Create"**

5. Configurar la contraseña:
   - Ve a la pestaña **"Credentials"**
   - Haz clic en **"Set password"**
   - **Password**: `password123`
   - **Password confirmation**: `password123`
   - **Temporary**: OFF (para que no pida cambiar la contraseña)
   - Haz clic en **"Save"**
   - Confirma en el diálogo

6. Asignar roles:
   - Ve a la pestaña **"Role mapping"**
   - Haz clic en **"Assign role"**
   - Filtra por "realm roles"
   - Selecciona **USER**
   - Haz clic en **"Assign"**

### Usuario 2: Administrador

Repite el proceso anterior pero:
- **Username**: `admin1`
- **Email**: `admin1@example.com`
- **First name**: María
- **Last name**: García
- **Password**: `admin123`
- **Roles**: Asigna tanto **USER** como **ADMIN**

## Paso 7: Verificar la Configuración

### Verificar el Client:

1. Ve a **Clients** > `spring-boot-client`
2. Verifica que tengas:
   - Client authentication: ON
   - Standard flow: Enabled
   - Direct access grants: Enabled
   - Valid redirect URIs configurado

### Verificar Roles:

1. Ve a **Realm roles**
2. Deberías ver: `USER` y `ADMIN`

### Verificar Usuarios:

1. Ve a **Users**
2. Deberías ver: `usuario1` y `admin1`

## Paso 8: Actualizar application.yml

Asegúrate de que tu `application.yml` tenga la configuración correcta:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: spring-boot-client
            client-secret: [el-secret-que-copiaste]
        provider:
          keycloak:
            issuer-uri: http://localhost:8080/realms/mi-realm
```

## Paso 9: Obtener un Token (Para Testing)

Puedes obtener un token usando cURL para probar la API:

```bash
curl -X POST 'http://localhost:8080/realms/mi-realm/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=spring-boot-client' \
  -d 'client_secret=TU_CLIENT_SECRET_AQUI' \
  -d 'grant_type=password' \
  -d 'username=usuario1' \
  -d 'password=password123'
```

Esto te devolverá un JSON con un `access_token`. Cópialo.

## Paso 10: Probar la API

### Test 1: Endpoint Público (No requiere token)

```bash
curl http://localhost:8081/public/hello
```

**Resultado esperado**: ✅ Debe funcionar sin autenticación

### Test 2: Endpoint de Usuario (Requiere token y rol USER)

```bash
curl http://localhost:8081/api/user/me \
  -H 'Authorization: Bearer TU_ACCESS_TOKEN_AQUI'
```

**Resultado esperado**: ✅ Debe retornar información del usuario

### Test 3: Endpoint de Admin (Requiere token y rol ADMIN)

Con usuario1 (solo tiene rol USER):
```bash
curl http://localhost:8081/api/admin/dashboard \
  -H 'Authorization: Bearer TOKEN_DE_USUARIO1'
```

**Resultado esperado**: ❌ Error 403 Forbidden (el usuario no tiene rol ADMIN)

Con admin1 (tiene rol ADMIN):
```bash
curl http://localhost:8081/api/admin/dashboard \
  -H 'Authorization: Bearer TOKEN_DE_ADMIN1'
```

**Resultado esperado**: ✅ Debe funcionar correctamente

## Troubleshooting (Solución de Problemas)

### Error: "Invalid token"

**Causa**: El token puede haber expirado o ser inválido.
**Solución**: Obtén un nuevo token.

### Error: "403 Forbidden"

**Causa**: El usuario no tiene el rol requerido.
**Solución**: Verifica que el usuario tenga los roles correctos en Keycloak.

### Error: "Connection refused to localhost:8080"

**Causa**: Keycloak no está ejecutándose.
**Solución**: Asegúrate de que Keycloak esté corriendo.

### Error: "Invalid redirect URI"

**Causa**: La URL de redirección no está configurada en el client.
**Solución**: Agrega `http://localhost:8081/*` a "Valid redirect URIs" en la configuración del client.

### No puedo ver los roles en el token

**Causa**: Los roles no están siendo incluidos en el token.
**Solución**: Ve a Clients > spring-boot-client > Client scopes > spring-boot-client-dedicated > Add mapper > Configure "realm roles" mapper.

## Recursos Útiles

- **Consola de Admin**: http://localhost:8080/admin
- **Decodificar JWT**: https://jwt.io
- **Documentación Keycloak**: https://www.keycloak.org/documentation
- **Spring Security OAuth2**: https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html

## Siguiente Paso

Una vez que tengas todo configurado, ¡estás listo para ejecutar la aplicación Spring Boot!

```bash
cd keycloak-spring-demo
./mvnw spring-boot:run
```

# Spring Boot + Keycloak - Versión Educativa Básica

> **Rama:** `main`
> **Nivel:** 🌱 Principiante
> **Propósito:** Primera toma de contacto con Keycloak y Spring Security

Esta es la implementación **MÁS SIMPLE** para aprender los conceptos básicos de autenticación con Keycloak.

---

## 🎯 ¿Qué Aprenderás?

Con esta rama aprenderás los **conceptos fundamentales**:

- ✅ Qué es un **token JWT** y cómo funciona
- ✅ Cómo **validar tokens** con Spring Security
- ✅ **Control de acceso basado en roles** (RBAC)
- ✅ Diferencia entre endpoints públicos y protegidos
- ✅ Arquitectura **STATELESS** (sin sesiones)
- ✅ Cómo Keycloak organiza **roles** en el token

**No incluye:** Gestión de login, redirecciones, cookies, Client Credentials, ni BFF.

---

## 📚 Ruta de Aprendizaje

```
┌─────────────────────────────────────────────────────────────┐
│  PASO 1: main (esta rama)                                   │
│  Nivel: 🌱 Principiante                                      │
│  ────────────────────────────────────────────────────────   │
│  ✓ Entiendes Resource Server                                │
│  ✓ Entiendes validación JWT                                 │
│  ✓ Entiendes roles y permisos                               │
│  ✓ Arquitectura STATELESS                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PASO 2: oauth2-resource-server                             │
│  Nivel: 🌿 Intermedio                                        │
│  ────────────────────────────────────────────────────────   │
│  ✓ Client Credentials (M2M)                                 │
│  ✓ Service Accounts                                         │
│  ✓ Comunicación servicio-a-servicio                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  PASO 3: oauth2-bff                                         │
│  Nivel: 🌳 Avanzado                                          │
│  ────────────────────────────────────────────────────────   │
│  ✓ Authorization Code Flow                                  │
│  ✓ Patrón BFF para SPAs                                     │
│  ✓ Cookies HttpOnly                                         │
│  ✓ STATEFUL (sesiones)                                      │
└─────────────────────────────────────────────────────────────┘
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

**Nota:** Mapeamos puerto `9090` del host → `8080` del contenedor.

### 2. Configurar Keycloak

Sigue la guía: **[SETUP.md](SETUP.md)**

Resumen:
1. Acceder a http://localhost:9090/admin
2. Crear realm: `mi-realm`
3. Crear client: `spring-boot-client`
4. Crear roles: `user`, `admin`
5. Crear usuarios de prueba

### 3. Ejecutar la Aplicación

```bash
./mvnw spring-boot:run
```

La API estará en: http://localhost:8081

### 4. Probar

Ver ejemplos completos en: **[USAGE.md](USAGE.md)**

**Prueba rápida:**
```bash
# Endpoint público (sin token)
curl http://localhost:8081/public/hello
```

---

## 🎓 Conceptos Clave

### Resource Server

Esta aplicación es un **Resource Server**:
- ✅ Valida tokens JWT
- ✅ Extrae roles del token
- ✅ Protege endpoints
- ❌ **NO** genera tokens (eso lo hace Keycloak)

### STATELESS

**Sin sesiones HTTP:**
- Cada request debe incluir el token en el header
- No se guarda estado en el servidor
- Ideal para APIs REST

### Flujo de Autenticación

```
1. Usuario → Obtiene token de Keycloak (directamente)
   POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token
   Body: grant_type=password, username=usuario1, password=password123

2. Keycloak → Devuelve token JWT

3. Usuario → Llama a la API con el token
   GET http://localhost:8081/api/user/me
   Header: Authorization: Bearer {token}

4. API → Valida token y responde
```

### Roles

**En Keycloak:**
- Creas roles: `user`, `admin`
- Asignas roles a usuarios

**En el token JWT:**
```json
{
  "realm_access": {
    "roles": ["user", "admin"]
  }
}
```

**En Spring Security:**
- `user` → `ROLE_USER`
- `admin` → `ROLE_ADMIN`

---

## 📂 Estructura del Proyecto

```
src/main/java/com/example/keycloak/
├── config/
│   └── SecurityConfig.java          # ⭐ Configuración de seguridad con MUCHOS comentarios
├── controller/
│   ├── PublicController.java        # Endpoints públicos (sin token)
│   ├── UserController.java          # Endpoints con ROLE_USER
│   └── AdminController.java         # Endpoints con ROLE_ADMIN
└── model/
    └── UserInfo.java

src/main/resources/
└── application.yml                  # ⭐ Configuración SIMPLE (solo issuer-uri)
```

---

## 🧪 Endpoints Disponibles

### Públicos (sin token)

```bash
GET /public/hello       # Saludo público
GET /public/info        # Información de la API
```

### Protegidos (requieren token + ROLE_USER)

```bash
GET /api/user/me        # Información del usuario autenticado
GET /api/user/dashboard # Dashboard de usuario
GET /api/user/profile   # Perfil del usuario
```

### Protegidos (requieren token + ROLE_ADMIN)

```bash
GET /api/admin/dashboard  # Dashboard de administración
GET /api/admin/users      # Lista de usuarios
```

---

## 🔑 Configuración Técnica

### application.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:9090/realms/mi-realm
```

**Eso es todo.** Spring obtiene automáticamente:
- `jwk-set-uri` (claves públicas para validar el token)
- `issuer` (emisor esperado en el token)
- Toda la configuración OpenID Connect

### SecurityConfig.java

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/public/**").permitAll()
            .requestMatchers("/api/user/**").hasRole("USER")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        )
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .csrf(csrf -> csrf.disable());

    return http.build();
}
```

**Características:**
- ✅ Resource Server (valida JWT)
- ✅ STATELESS (sin sesiones)
- ✅ CSRF deshabilitado (apropiado para APIs REST)
- ❌ NO oauth2Login (no gestiona autenticación)

---

## 🐛 Troubleshooting

### Error: 401 Unauthorized

**Causa:** Token ausente, inválido o expirado.

**Solución:**
```bash
# Verificar que envías el header correcto
-H "Authorization: Bearer {token}"

# Obtener un nuevo token
curl -X POST http://localhost:9090/realms/mi-realm/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=spring-boot-client" \
  -d "username=usuario1" \
  -d "password=password123"
```

### Error: 403 Forbidden

**Causa:** Token válido pero sin el rol requerido.

**Solución:**
- Verificar roles del usuario en Keycloak
- Asignar el rol necesario: `user` o `admin`

### Error: Invalid token signature

**Causa:** URL de Keycloak incorrecta.

**Solución:**
```bash
# Verificar que Keycloak está en puerto 9090
curl http://localhost:9090/realms/mi-realm/.well-known/openid-configuration
```

---

## 📖 Documentación

- **[SETUP.md](SETUP.md)** - Configuración paso a paso de Keycloak
- **[USAGE.md](USAGE.md)** - Ejemplos de uso con cURL y Postman

---

## 🎯 Limitaciones de Esta Versión

Esta es una versión **educativa básica**. No incluye:

❌ Client Credentials (M2M)
❌ Service Accounts
❌ Authorization Code Flow
❌ Patrón BFF
❌ Cookies HttpOnly
❌ CORS configurado

**Para aprender estos conceptos:** Ver ramas `oauth2-resource-server` y `oauth2-bff`

---

## 🚀 Siguiente Paso

Una vez que domines esta rama, continúa con:

### Rama `oauth2-resource-server`

**Qué añade:**
- Client Credentials (M2M)
- Service Accounts
- Documentación extensa de M2M

**Cuándo usarla:**
- APIs backend que se comunican entre sí
- Microservicios
- Cron jobs que llaman APIs

```bash
git checkout oauth2-resource-server
```

### Rama `oauth2-bff`

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
git checkout oauth2-bff
```

---

## 🛠️ Tecnologías

- **Spring Boot:** 3.2.0
- **Spring Security:** 6.2.0
- **Keycloak:** 23.0.0+ (puerto 9090)
- **Java:** 17+

---

## ✅ Checklist de Aprendizaje

Marca lo que ya dominas:

**Conceptos básicos:**
- [ ] Entiendo qué es un token JWT
- [ ] Sé cómo validar un token con Spring Security
- [ ] Entiendo STATELESS vs STATEFUL
- [ ] Sé configurar roles en Keycloak
- [ ] Entiendo `@PreAuthorize` y control de acceso

**Siguientes pasos:**
- [ ] Probé obtener un token de Keycloak
- [ ] Probé llamar endpoints protegidos
- [ ] Entiendo cómo Spring extrae roles del token
- [ ] Listo para pasar a `oauth2-resource-server`

---

**¿Listo para más?** → Explora las ramas avanzadas:
- **oauth2-resource-server** - APIs M2M
- **oauth2-bff** - SPAs modernas

**¿Dudas?** Revisa [SETUP.md](SETUP.md) y [USAGE.md](USAGE.md)

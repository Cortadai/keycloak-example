# Spring Boot + Keycloak - Demo Básico

**Demo educativo básico** de autenticación y autorización con Spring Boot y Keycloak.

> ⚠️ **IMPORTANTE**: Esta es una implementación **básica con fines educativos**.
> Para **producción**, usa la rama **[oauth2-authorization-code](../../tree/oauth2-authorization-code)** que implementa best practices:
> - ✅ OAuth2 Authorization Code Flow
> - ✅ Client secret en variables de entorno
> - ✅ Configuración optimizada con `issuer-uri`
> - ✅ Dual authentication (OAuth2 Login + Resource Server)

## 📋 Descripción

Este proyecto es una implementación educativa básica que demuestra:

- Validación de tokens JWT con Keycloak
- Control de acceso basado en roles (RBAC)
- Endpoints públicos y protegidos
- Configuración básica de Spring Security con Keycloak

## 🚀 Quick Start

### Prerrequisitos

- Java 17+
- Maven 3.8+
- Docker (para Keycloak)

### 1. Iniciar Keycloak

```bash
docker run -p 8080:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:23.0.0 start-dev
```

Accede a: http://localhost:8080
- Usuario: `admin`
- Contraseña: `admin`

### 2. Configurar Keycloak

Ver guía detallada en **[SETUP.md](SETUP.md)**

Pasos básicos:
1. Crear realm `mi-realm`
2. Crear client `spring-boot-client`
3. Crear roles `USER` y `ADMIN`
4. Crear usuarios de prueba

### 3. Configurar la Aplicación

Edita `src/main/resources/application.yml` y reemplaza:

```yaml
client-secret: tu-client-secret-aqui  # ← Pega tu client secret aquí
```

**⚠️ NOTA**: Este método NO es recomendado para producción. Ver rama `oauth2-authorization-code` para implementación correcta con variables de entorno.

### 4. Ejecutar

```bash
./mvnw spring-boot:run
```

La aplicación estará en: http://localhost:8081

### 5. Probar

**Endpoint público:**
```bash
curl http://localhost:8081/public/hello
```

**Obtener token y probar endpoint protegido:**

Ver ejemplos completos en **[USAGE.md](USAGE.md)**

## 📁 Estructura del Proyecto

```
keycloak-spring-demo/
├── src/main/java/com/example/keycloak/
│   ├── config/
│   │   └── SecurityConfig.java       # Configuración de seguridad
│   ├── controller/
│   │   ├── PublicController.java     # Endpoints públicos
│   │   ├── UserController.java       # Endpoints USER
│   │   └── AdminController.java      # Endpoints ADMIN
│   └── model/
│       └── UserInfo.java
├── src/main/resources/
│   └── application.yml               # ⚠️ Client secret hardcodeado
├── README.md                         # Este archivo
├── SETUP.md                          # Guía de configuración
└── USAGE.md                          # Ejemplos de uso
```

## 🧪 Testing

### Usuarios de Prueba

| Usuario | Contraseña | Roles |
|---------|------------|-------|
| usuario1 | password123 | USER |
| admin1 | admin123 | USER, ADMIN |

### Endpoints Disponibles

**Públicos:**
- `GET /public/hello`
- `GET /public/info`

**Protegidos (ROLE_USER):**
- `GET /api/user/me`
- `GET /api/user/dashboard`
- `GET /api/user/profile`

**Protegidos (ROLE_ADMIN):**
- `GET /api/admin/dashboard`
- `GET /api/admin/users`

## 📚 Documentación

- **[SETUP.md](SETUP.md)** - Configuración de Keycloak y la aplicación
- **[USAGE.md](USAGE.md)** - Ejemplos de uso con cURL y Postman

## ⚠️ Limitaciones de esta Implementación

Esta implementación básica tiene las siguientes limitaciones:

1. **Client secret hardcodeado** en `application.yml` ❌
   - Riesgo: Se puede commitear accidentalmente al repositorio
   - Solución: Ver rama `oauth2-authorization-code`

2. **Configuración verbose** ❌
   - Define todos los endpoints manualmente
   - Solución: Usar solo `issuer-uri` (ver rama `oauth2-authorization-code`)

3. **No implementa OAuth2 Login** ❌
   - Solo Resource Server (Bearer tokens)
   - No soporta login desde navegador
   - Solución: Ver rama `oauth2-authorization-code`

4. **Solo Resource Owner Password Grant** ❌
   - Flujo deprecado, no recomendado para producción
   - Solución: Ver rama `oauth2-authorization-code` que implementa Authorization Code Flow

## 🚀 Migrar a Producción

Para llevar este proyecto a producción:

1. **Cambia a la rama recomendada:**
   ```bash
   git checkout oauth2-authorization-code
   ```

2. **Revisa las mejoras:**
   - OAuth2 Authorization Code Flow
   - Client secret en variables de entorno
   - Configuración simplificada
   - Dual authentication (OAuth2 Login + Resource Server)
   - Documentación completa

## 🛠️ Tecnologías

- **Spring Boot 3.2.0**
- **Spring Security 6.2.0**
- **Keycloak 23.0.0**
- **JWT**
- **OAuth2/OIDC**

## 🎯 Uso Recomendado

**Esta rama (main):**
- ✅ Aprendizaje inicial de Keycloak
- ✅ Entender conceptos básicos
- ✅ Experimentación rápida
- ❌ NO para producción

**Rama oauth2-authorization-code:**
- ✅ Implementación production-ready
- ✅ Best practices de OAuth2
- ✅ Seguridad mejorada
- ✅ Para aplicaciones reales

---

**¿Listo para producción?** → Revisa la rama **[oauth2-authorization-code](../../tree/oauth2-authorization-code)** 🚀

# Aprender Spring Boot con Keycloak

Un proyecto completo para aprender a integrar **Spring Boot** con **Keycloak** para autenticación y autorización.


---

## 🎯 ¿Qué aprenderás?

- ✅ **Autenticación moderna** con OAuth 2.0 y OpenID Connect
- ✅ **Validación de tokens JWT** en Spring Boot
- ✅ **Control de acceso basado en roles** (RBAC)
- ✅ **Integración completa** Spring Security + Keycloak
- ✅ **Mejores prácticas** de seguridad
- ✅ **Ejemplos prácticos** con código comentado

## 🚀 Inicio Rápido

### Montar un Keycloak con Docker

1. **Inicia Keycloak:**
   ```bash
   docker run -p 8080:8080 \
     -e KEYCLOAK_ADMIN=admin \
     -e KEYCLOAK_ADMIN_PASSWORD=admin \
     quay.io/keycloak/keycloak:latest start-dev
   ```

2. **Lee la guía:**
   ```bash
   EMPIEZA_AQUI.md
   ```

3. **Configura Keycloak:**
   - Accede a http://localhost:8080
   - Sigue `CONFIGURACION_KEYCLOAK.md`

4. **Ejecuta la aplicación:**
   ```bash
   cd keycloak-spring-demo
   ./mvnw spring-boot:run
   ```

5. **Prueba:**
   ```bash
   curl http://localhost:8081/public/hello
   ```

## 📚 Documentación

### Guías de Aprendizaje

| Documento | Descripción | Nivel |
|-----------|-------------|-------|
| [EMPIEZA_AQUI.md](EMPIEZA_AQUI.md) | 🎓 **Comienza aquí** - Ruta de aprendizaje | Principiante |
| [GUIA_APRENDIZAJE.md](GUIA_APRENDIZAJE.md) | 📖 Conceptos básicos de Spring Boot + Keycloak | Principiante |
| [CONFIGURACION_KEYCLOAK.md](CONFIGURACION_KEYCLOAK.md) | ⚙️ Configuración paso a paso de Keycloak | Principiante |
| [EJEMPLOS_PRUEBA.md](EJEMPLOS_PRUEBA.md) | 🧪 Ejemplos prácticos con cURL y Postman | Intermedio |
| [CONCEPTOS_AVANZADOS.md](CONCEPTOS_AVANZADOS.md) | 🚀 Temas avanzados y producción | Avanzado |


## 🏗️ Arquitectura del Proyecto

```
┌─────────────────────────────────────────────────────────┐
│                      Cliente                            │
│            (cURL / Postman / Browser)                   │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┼──────────────┐
         │                          │
         ▼                          ▼
┌─────────────────┐      ┌─────────────────────┐
│    Keycloak     │      │   Spring Boot App   │
│   (Port 8080)   │◄────►│    (Port 8081)      │
│                 │      │                     │
│ • Autenticación │      │ • Endpoints REST    │
│ • Gestión Users │      │ • Validación JWT    │
│ • Genera Tokens │      │ • RBAC (Roles)      │
└─────────────────┘      └─────────────────────┘
```

## 📁 Estructura del Proyecto

```
keycloak/
└── 📦 keycloak-spring-demo/         ← Proyecto Spring Boot
    ├── src/
    │   └── main/
    │       ├── java/com/example/keycloak/
    │       │   ├── config/
    │       │   │   └── SecurityConfig.java        # ⭐ Configuración de seguridad
    │       │   ├── controller/
    │       │   │   ├── PublicController.java      # 🌐 Endpoints públicos
    │       │   │   ├── UserController.java        # 👤 Endpoints de usuario
    │       │   │   └── AdminController.java       # 🔐 Endpoints de admin
    │       │   └── model/
    │       │       └── UserInfo.java
    │       └── resources/
    │           └── application.yml                # ⚙️  Configuración
    ├─── pom.xml                                    # 📦 Dependencias
    ├── 📄 README.md                     ← Estás aquí
    ├── 🎓 EMPIEZA_AQUI.md               ← Lee esto primero
    ├── 📖 GUIA_APRENDIZAJE.md           ← Conceptos básicos
    ├── ⚙️  CONFIGURACION_KEYCLOAK.md    ← Setup de Keycloak
    ├── 🧪 EJEMPLOS_PRUEBA.md            ← Ejemplos prácticos
    ├── 🚀 CONCEPTOS_AVANZADOS.md        ← Temas avanzados
```



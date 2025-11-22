# 🚀 Empieza Aquí - Spring Boot + Keycloak

¡Bienvenido a tu guía completa de aprendizaje de Spring Boot con Keycloak!

## 📚 ¿Qué encontrarás en este proyecto?

Este es un proyecto educativo completo con:

- ✅ Proyecto Spring Boot funcional
- ✅ Integración completa con Keycloak
- ✅ Ejemplos de endpoints públicos, privados y para admins
- ✅ Documentación detallada paso a paso
- ✅ Scripts de inicio rápido
- ✅ Ejemplos de prueba con cURL y Postman
- ✅ Conceptos avanzados

## 🎯 Ruta de Aprendizaje Recomendada

### Nivel 1: Fundamentos

1. **Lee la guía de aprendizaje**
   ```bash
   GUIA_APRENDIZAJE.md
   ```
   - Aprende qué es Keycloak
   - Entiende los conceptos básicos (Realms, Clients, Roles)
   - Comprende el flujo de autenticación

2. **Inicia Keycloak rápidamente**

### Nivel 2: Configuración

3. **Configura Keycloak paso a paso**
   ```bash
   CONFIGURACION_KEYCLOAK.md
   ```
   - Crea tu primer Realm
   - Configura un Client
   - Crea roles y usuarios de prueba
   - Obtén tu primer token

### Nivel 3: Desarrollo

4. **Explora el código del proyecto**
   - Revisa `SecurityConfig.java` - El corazón de la seguridad
   - Lee los controladores (PublicController, UserController, AdminController)
   - Entiende cómo se validan los tokens JWT

5. **Ejecuta la aplicación**
   ```bash
   # Asegúrate de haber actualizado application.yml con tu client-secret
   ./mvnw spring-boot:run
   ```

### Nivel 4: Pruebas

6. **Prueba todos los endpoints**
   ```bash
   EJEMPLOS_PRUEBA.md
   ```
   - Endpoints públicos (sin token)
   - Endpoints de usuario (con token USER)
   - Endpoints de admin (con token ADMIN)
   - Casos de error (403, 401)

### Nivel 5: Avanzado

7. **Conceptos avanzados**
   ```bash
   CONCEPTOS_AVANZADOS.md
   ```
   - Client Scopes y Mappers
   - Service Accounts
   - Authorization Services
   - Microservicios
   - High Availability

  

## 🎓 Objetivos de Aprendizaje

Al completar este tutorial, podrás:

- ✅ Entender qué es Keycloak y por qué usarlo
- ✅ Configurar Keycloak desde cero
- ✅ Integrar Spring Boot con Keycloak
- ✅ Implementar autenticación con OAuth 2.0 / OpenID Connect
- ✅ Validar tokens JWT
- ✅ Implementar control de acceso basado en roles (RBAC)
- ✅ Proteger endpoints con Spring Security
- ✅ Obtener y usar tokens de acceso
- ✅ Manejar diferentes niveles de autorización
- ✅ Probar endpoints con diferentes métodos

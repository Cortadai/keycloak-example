# Guía de Aprendizaje: Spring Boot + Keycloak

## ¿Qué es Keycloak?

Keycloak es un servidor de gestión de identidad y acceso (IAM) de código abierto que proporciona:
- **Autenticación**: Verificar quién es el usuario (login)
- **Autorización**: Determinar qué puede hacer el usuario
- **Single Sign-On (SSO)**: Iniciar sesión una vez para acceder a múltiples aplicaciones
- **Gestión de usuarios**: Administrar usuarios, roles y permisos
- **Integración con protocolos estándar**: OAuth 2.0, OpenID Connect, SAML 2.0

## ¿Por qué usar Keycloak con Spring Boot?

Spring Boot es excelente para crear aplicaciones, pero la seguridad puede ser compleja. Keycloak te permite:
- No tener que implementar tu propio sistema de login
- Gestión centralizada de usuarios
- Seguridad robusta y probada
- Facilita la implementación de microservicios seguros

## Conceptos Clave

### 1. **Realm** (Reino)
- Es un espacio aislado donde gestionas usuarios, credenciales, roles y grupos
- Cada realm es independiente
- Ejemplo: Puedes tener un realm para "desarrollo" y otro para "producción"

### 2. **Client** (Cliente)
- Representa tu aplicación Spring Boot
- Define cómo tu aplicación se comunica con Keycloak
- Tipos comunes:
  - **confidential**: Para aplicaciones backend (Spring Boot)
  - **public**: Para aplicaciones frontend (React, Angular)

### 3. **Roles** (Roles)
- Definen permisos
- Tipos:
  - **Realm roles**: Globales al realm
  - **Client roles**: Específicos de una aplicación

### 4. **Tokens**
- **Access Token**: JWT que contiene información del usuario y sus roles
- **Refresh Token**: Para obtener nuevos access tokens sin reautenticar
- **ID Token**: Información sobre la identidad del usuario

## Flujo de Autenticación

```
1. Usuario intenta acceder a un endpoint protegido
2. Spring Boot redirige a Keycloak para login
3. Usuario ingresa credenciales en Keycloak
4. Keycloak valida y genera tokens (JWT)
5. Keycloak redirige de vuelta a Spring Boot con el token
6. Spring Boot valida el token
7. Si es válido, permite el acceso
```

## Arquitectura del Proyecto de Ejemplo

```
keycloak-spring-demo/
├── src/main/java/com/example/keycloak/
│   ├── config/
│   │   └── SecurityConfig.java       # Configuración de seguridad
│   ├── controller/
│   │   ├── PublicController.java     # Endpoints públicos
│   │   ├── UserController.java       # Endpoints para usuarios
│   │   └── AdminController.java      # Endpoints para administradores
│   ├── model/
│   │   └── UserInfo.java             # Modelo de datos
│   └── KeycloakDemoApplication.java  # Aplicación principal
├── src/main/resources/
│   └── application.yml               # Configuración de la app
└── pom.xml                           # Dependencias Maven
```

## Pasos Siguientes

1. **Instalar Keycloak**: Necesitas un servidor Keycloak corriendo
2. **Configurar Realm y Client**: En la consola de administración de Keycloak
3. **Crear el proyecto Spring Boot**: Con las dependencias necesarias
4. **Configurar la integración**: En application.yml
5. **Implementar seguridad**: Proteger endpoints según roles
6. **Probar**: Usando Postman o el navegador

## Comandos Útiles

### Iniciar Keycloak con Docker:
```bash
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:latest start-dev
```

### Acceder a la consola de administración:
- URL: http://localhost:8080
- Usuario: admin
- Contraseña: admin

### Ejecutar la aplicación Spring Boot:
```bash
./mvnw spring-boot:run
```

## Recursos Adicionales

- Documentación oficial de Keycloak: https://www.keycloak.org/documentation
- Spring Security OAuth2: https://spring.io/guides/tutorials/spring-boot-oauth2/
- JWT.io: Para decodificar y validar tokens JWT

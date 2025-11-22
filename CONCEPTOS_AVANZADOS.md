# Conceptos Avanzados - Spring Boot + Keycloak

Una vez que hayas dominado los conceptos básicos, aquí hay temas más avanzados para explorar.

## 1. Client Scopes y Mappers

### ¿Qué son los Client Scopes?

Los Client Scopes definen qué información se incluye en el token JWT.

### Configurar Mappers Personalizados

Los mappers te permiten agregar información personalizada al token.

#### Ejemplo: Agregar atributos personalizados al token

1. En Keycloak, ve a **Clients** > `spring-boot-client` > **Client scopes**
2. Click en `spring-boot-client-dedicated`
3. Click en **Add mapper** > **By configuration** > **User Attribute**
4. Configuración:
   - **Name**: department
   - **User Attribute**: department
   - **Token Claim Name**: department
   - **Claim JSON Type**: String
   - **Add to ID token**: ON
   - **Add to access token**: ON
   - **Add to userinfo**: ON

#### Usar en Spring Boot:

```java
@GetMapping("/department")
public String getDepartment(Authentication authentication) {
    Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
    return jwt.getClaimAsString("department");
}
```

## 2. Múltiples Realms

Puedes tener diferentes realms para diferentes entornos:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak-dev:
            client-id: dev-client
            # ... configuración para desarrollo
          keycloak-prod:
            client-id: prod-client
            # ... configuración para producción
```

## 3. Service Accounts

Los Service Accounts permiten autenticación máquina-a-máquina.

### Configuración en Keycloak:

1. En **Clients** > `spring-boot-client`
2. **Settings**:
   - **Service accounts enabled**: ON
3. Guarda
4. Ve a **Service Account Roles** para asignar roles

### Usar en Spring Boot:

```java
@Bean
public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

    oauth2Client.setDefaultClientRegistrationId("keycloak");

    return WebClient.builder()
        .apply(oauth2Client.oauth2Configuration())
        .build();
}
```

## 4. Grupos y Jerarquía de Roles

### Crear Grupos en Keycloak:

1. Ve a **Groups** > **Create group**
2. Nombre: `Developers`
3. Asigna roles al grupo
4. Agrega usuarios al grupo

### Beneficios:

- Gestión centralizada de permisos
- Jerarquía de accesos
- Facilita la administración de muchos usuarios

## 5. Identity Providers (SSO Externo)

Keycloak puede integrarse con proveedores externos:

### Proveedores Soportados:

- Google
- Facebook
- GitHub
- Microsoft
- SAML providers
- Cualquier proveedor OAuth 2.0 / OpenID Connect

### Configurar Google Login:

1. En Keycloak, ve a **Identity Providers**
2. Selecciona **Google**
3. Ingresa Client ID y Client Secret de Google
4. Guarda

Ahora los usuarios pueden hacer login con Google.

## 6. Custom Authentication Flows

Puedes personalizar el flujo de autenticación:

### Casos de uso:

- Autenticación de dos factores (2FA)
- Validaciones personalizadas
- Integración con sistemas legacy

### Ejemplo: Agregar 2FA

1. En Keycloak, ve a **Authentication** > **Flows**
2. Copia el flow "Browser"
3. Agrega "OTP Form" al flow
4. Configura la política de OTP

## 7. Authorization Services (Fine-Grained Authorization)

Keycloak ofrece autorización más granular que solo roles.

### Conceptos:

- **Resources**: Entidades a proteger (ej: documentos, archivos)
- **Scopes**: Acciones sobre recursos (ej: read, write, delete)
- **Policies**: Reglas que definen acceso
- **Permissions**: Asocian policies con resources

### Habilitar Authorization:

1. En **Clients** > `spring-boot-client`
2. **Settings** > **Authorization Enabled**: ON

### Ejemplo en Spring Boot:

```java
@GetMapping("/document/{id}")
@PreAuthorize("hasAuthority('SCOPE_document:read')")
public Document getDocument(@PathVariable String id) {
    return documentService.findById(id);
}
```

## 8. Events y Auditoría

Keycloak registra eventos para auditoría.

### Habilitar Events:

1. Ve a **Realm Settings** > **Events**
2. **User events**: ON
3. **Save events**: ON
4. Selecciona eventos a guardar

### Tipos de eventos:

- LOGIN
- LOGOUT
- UPDATE_PASSWORD
- REGISTER
- etc.

### Ver eventos:

**Events** > **Login events** / **Admin events**

## 9. Temas y Personalización

Personaliza la UI de Keycloak:

### Directorios de temas:

```
keycloak/
  themes/
    mi-tema/
      login/
        theme.properties
        login.ftl
      account/
      email/
```

### Aplicar tema:

1. **Realm Settings** > **Themes**
2. Selecciona tu tema personalizado

## 10. Rate Limiting y Seguridad

### Protección contra Brute Force:

1. **Realm Settings** > **Security Defenses** > **Brute Force Detection**
2. Configuración:
   - **Permanent Lockout**: OFF
   - **Max Login Failures**: 5
   - **Wait Increment**: 60 seconds
   - **Max Wait**: 900 seconds

### Password Policies:

1. **Authentication** > **Policies**
2. Agrega políticas:
   - Minimum Length
   - Require Uppercase
   - Require Digits
   - Password Blacklist

## 11. Token Exchange

Permite intercambiar tokens entre diferentes clientes.

### Caso de uso:

Microservicio A tiene un token y necesita llamar a Microservicio B.

### Configuración:

```bash
curl -X POST \
  http://localhost:8080/realms/mi-realm/protocol/openid-connect/token \
  -d 'client_id=service-a' \
  -d 'client_secret=secret' \
  -d 'grant_type=urn:ietf:params:oauth:grant-type:token-exchange' \
  -d 'subject_token=ORIGINAL_TOKEN' \
  -d 'requested_token_type=urn:ietf:params:oauth:token-type:access_token' \
  -d 'audience=service-b'
```

## 12. Session Management

### Configurar duración de sesiones:

1. **Realm Settings** > **Sessions**
2. Configuración:
   - **SSO Session Idle**: 30 minutes
   - **SSO Session Max**: 10 hours
   - **Access Token Lifespan**: 5 minutes

### Cerrar sesiones remotamente:

```java
@PostMapping("/logout-user/{userId}")
@PreAuthorize("hasRole('ADMIN')")
public void logoutUser(@PathVariable String userId) {
    keycloakService.revokeUserSessions(userId);
}
```

## 13. Integración con Bases de Datos

### User Federation

Conecta Keycloak con tu base de datos existente:

1. **User Federation** > **Add provider** > **LDAP** o **Custom**
2. Configura la conexión
3. Los usuarios se sincronizan automáticamente

### Ventajas:

- No necesitas migrar usuarios
- Sincronización bidireccional
- Autenticación contra sistemas legacy

## 14. Microservicios con Keycloak

### Arquitectura típica:

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│ Frontend │────>│ API      │────>│ Keycloak │
│ (React)  │     │ Gateway  │     │          │
└──────────┘     └──────────┘     └──────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
   ┌────▼───┐   ┌────▼───┐   ┌────▼───┐
   │ Service│   │ Service│   │ Service│
   │   A    │   │   B    │   │   C    │
   └────────┘   └────────┘   └────────┘
```

### Cada microservicio:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://keycloak:8080/realms/mi-realm
```

### Spring Cloud Gateway con Keycloak:

```java
@Configuration
public class GatewaySecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/public/**").permitAll()
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}
```

## 15. Testing con Keycloak

### Testcontainers (Testing con Docker):

```java
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class KeycloakIntegrationTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer()
        .withRealmImportFile("realm-export.json");

    @Test
    void testProtectedEndpoint() {
        String token = getTokenFromKeycloak();

        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/api/user/me")
        .then()
            .statusCode(200);
    }
}
```

### Mocking JWT para tests unitarios:

```java
@Test
@WithMockUser(roles = "USER")
void testUserEndpoint() throws Exception {
    mockMvc.perform(get("/api/user/dashboard"))
        .andExpect(status().isOk());
}
```

## 16. Admin REST API

Keycloak ofrece una API REST completa para administración.

### Obtener access token de admin:

```bash
curl -X POST 'http://localhost:8080/realms/master/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=admin-cli' \
  -d 'username=admin' \
  -d 'password=admin' \
  -d 'grant_type=password'
```

### Crear usuario via API:

```bash
curl -X POST 'http://localhost:8080/admin/realms/mi-realm/users' \
  -H 'Authorization: Bearer ADMIN_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "newuser",
    "email": "newuser@example.com",
    "enabled": true,
    "credentials": [{
      "type": "password",
      "value": "password123",
      "temporary": false
    }]
  }'
```

### En Spring Boot:

```java
@Service
public class KeycloakAdminService {

    private final Keycloak keycloak;

    public KeycloakAdminService() {
        this.keycloak = KeycloakBuilder.builder()
            .serverUrl("http://localhost:8080")
            .realm("master")
            .username("admin")
            .password("admin")
            .clientId("admin-cli")
            .build();
    }

    public void createUser(String username, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);

        RealmResource realm = keycloak.realm("mi-realm");
        realm.users().create(user);
    }
}
```

## 17. Performance y Optimización

### Caché de tokens:

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("jwks");
    }
}
```

### Connection pooling:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://localhost:8080/realms/mi-realm/protocol/openid-connect/certs
          # Configurar timeout y pooling
```

## 18. High Availability (HA)

Para producción, necesitas múltiples instancias de Keycloak.

### Arquitectura HA:

```
        ┌───────────────┐
        │ Load Balancer │
        └───────┬───────┘
                │
    ┌───────────┴───────────┐
    │                       │
┌───▼──────┐        ┌──────▼───┐
│ Keycloak │        │ Keycloak │
│Instance 1│        │Instance 2│
└───┬──────┘        └──────┬───┘
    │                      │
    └──────────┬───────────┘
               │
        ┌──────▼──────┐
        │  Database   │
        │ (Shared)    │
        └─────────────┘
```

### Requisitos:

- Base de datos compartida (PostgreSQL, MySQL)
- Caché distribuido (Infinispan)
- Sticky sessions en el load balancer

## 19. Monitoreo y Métricas

### Exportar métricas de Keycloak:

```bash
docker run -p 8080:8080 \
  -e KC_METRICS_ENABLED=true \
  -e KC_HEALTH_ENABLED=true \
  quay.io/keycloak/keycloak:latest \
  start-dev
```

### Endpoints:

- Métricas: `http://localhost:8080/metrics`
- Health: `http://localhost:8080/health`

### Integración con Prometheus:

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'keycloak'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
```

## 20. Mejores Prácticas

### Seguridad:

1. ✅ Usa HTTPS en producción
2. ✅ Rotación regular de client secrets
3. ✅ Tokens de corta duración (5-15 min)
4. ✅ Refresh tokens con rotación
5. ✅ Habilita brute force protection
6. ✅ Implementa rate limiting

### Performance:

1. ✅ Caché de claves públicas (JWKS)
2. ✅ Connection pooling
3. ✅ Stateless sessions
4. ✅ Database indexing
5. ✅ CDN para recursos estáticos

### Operaciones:

1. ✅ Backups regulares del realm
2. ✅ Monitoreo y alertas
3. ✅ Logs centralizados
4. ✅ Disaster recovery plan
5. ✅ Automatización (IaC)

## Próximos Pasos

1. Implementa algunos de estos conceptos avanzados
2. Experimenta con diferentes configuraciones
3. Lee la documentación oficial de Keycloak
4. Únete a la comunidad de Keycloak

## Recursos

- [Keycloak Documentation](https://www.keycloak.org/documentation)
- [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html)
- [Keycloak GitHub](https://github.com/keycloak/keycloak)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/index.html)

---
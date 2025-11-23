package com.example.keycloak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ============================================================================
 * Configuración de Seguridad para SPA con Authorization Code + PKCE
 * ============================================================================
 *
 * Esta configuración implementa un Resource Server que valida tokens JWT
 * provenientes de un SPA (Angular/React/Vue) que usa Authorization Code Flow + PKCE.
 *
 * ¿Qué hace esta configuración?
 * 1. Valida tokens JWT que vienen en el header Authorization
 * 2. Extrae roles del token (realm_access.roles y resource_access)
 * 3. Protege endpoints según roles (USER, ADMIN)
 * 4. Permite CORS para el SPA
 *
 * ¿Qué NO hace?
 * - NO gestiona el login (el SPA lo hace directamente con Keycloak)
 * - NO crea sesiones HTTP (STATELESS)
 * - NO usa cookies (el JWT está en localStorage del SPA)
 * - NO implementa BFF (el token es accesible desde JavaScript)
 *
 * Diferencias con otras ramas:
 * - main: Resource Server básico educativo
 * - oauth2-resource-server: M2M con Client Credentials
 * - oauth2-bff: BFF con cookies HttpOnly (más seguro)
 * - oauth2-spa-pkce (esta rama): SPA tradicional con PKCE
 *
 * @Configuration - Indica que esta clase contiene configuración de Spring
 * @EnableWebSecurity - Habilita la seguridad web de Spring Security
 * @EnableMethodSecurity - Permite usar @PreAuthorize en los métodos
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configuración principal de seguridad para SPA.
     *
     * Define:
     * 1. Qué endpoints son públicos y cuáles requieren autenticación
     * 2. Qué roles se necesitan para cada endpoint
     * 3. Cómo validar tokens JWT
     * 4. CORS para permitir peticiones desde el SPA
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS: Permitir peticiones desde el SPA (Angular en localhost:4200)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 1. CONFIGURACIÓN DE AUTORIZACIÓN
            // Define quién puede acceder a qué endpoints
            .authorizeHttpRequests(auth -> auth
                // Endpoints PÚBLICOS (sin token)
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/", "/error").permitAll()

                // Endpoints que requieren rol USER
                // El usuario debe tener "user" en realm_access.roles de Keycloak
                .requestMatchers("/api/user/**").hasRole("USER")

                // Endpoints que requieren rol ADMIN
                // El usuario debe tener "admin" en realm_access.roles de Keycloak
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Cualquier otro endpoint requiere estar autenticado
                .anyRequest().authenticated()
            )

            // 2. CONFIGURACIÓN DE RESOURCE SERVER
            // Valida tokens JWT que vienen en: Authorization: Bearer {token}
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Usar nuestro convertidor personalizado para extraer roles de Keycloak
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // 3. CONFIGURACIÓN DE SESIONES
            // STATELESS = No crear sesiones HTTP
            // El SPA debe incluir el token JWT en cada petición
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 4. DESHABILITAR CSRF
            // Para APIs REST sin formularios HTML, CSRF no es necesario
            // El SPA gestiona la autenticación con PKCE
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Configuración de CORS para permitir peticiones desde el SPA.
     *
     * IMPORTANTE para SPAs:
     * - allowCredentials NO es necesario (no usamos cookies)
     * - allowedOrigins puede ser específico del SPA
     * - En producción, usar el dominio real del SPA
     *
     * @return Configuración de CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir peticiones desde el SPA (localhost:4200 en desarrollo)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",    // Cualquier puerto localhost
                "http://127.0.0.1:*"     // También 127.0.0.1
        ));

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // Headers permitidos
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept"
        ));

        // Headers que el navegador puede leer
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization"
        ));

        // NO necesitamos credentials (no usamos cookies)
        configuration.setAllowCredentials(false);

        // Tiempo de cache de la configuración CORS (1 hora)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/public/**", configuration);

        return source;
    }

    /**
     * Convertidor que extrae roles del token JWT.
     *
     * ¿Por qué es necesario?
     * - Keycloak guarda roles en "realm_access.roles" y "resource_access"
     * - Spring Security los busca en "scope" por defecto
     * - Este convertidor le dice a Spring dónde buscar los roles
     *
     * Convierte:
     * - "user" (en Keycloak) → "ROLE_USER" (en Spring Security)
     * - "admin" (en Keycloak) → "ROLE_ADMIN" (en Spring Security)
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Convertidor por defecto (extrae scopes)
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Crear el convertidor principal
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        // Configurar cómo extraer las autoridades (roles)
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extraer roles de diferentes lugares del token
            Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt.getClaims());
            Collection<GrantedAuthority> clientRoles = extractClientRoles(jwt.getClaims());
            Collection<GrantedAuthority> scopes = grantedAuthoritiesConverter.convert(jwt);

            // Combinar todos en una sola lista
            return Stream.of(realmRoles, clientRoles, scopes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        });

        return jwtAuthenticationConverter;
    }

    /**
     * Extrae roles del realm desde el token JWT.
     *
     * Busca en: token["realm_access"]["roles"]
     *
     * Ejemplo de token:
     * {
     *   "realm_access": {
     *     "roles": ["user", "admin"]
     *   }
     * }
     *
     * Resultado: ["ROLE_USER", "ROLE_ADMIN"]
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Map<String, Object> claims) {
        // Obtener realm_access del token
        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");

        // Si no existe o no tiene roles, retornar lista vacía
        if (realmAccess == null || realmAccess.get("roles") == null) {
            return List.of();
        }

        // Obtener la lista de roles
        List<String> roles = (List<String>) realmAccess.get("roles");

        // Convertir cada rol a GrantedAuthority con prefijo ROLE_
        // "user" → "ROLE_USER"
        // "admin" → "ROLE_ADMIN"
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    /**
     * Extrae roles del cliente desde el token JWT.
     *
     * Busca en: token["resource_access"]["{client-id}"]["roles"]
     *
     * Ejemplo de token:
     * {
     *   "resource_access": {
     *     "spring-boot-client": {
     *       "roles": ["manager"]
     *     }
     *   }
     * }
     *
     * Resultado: ["ROLE_MANAGER"]
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractClientRoles(Map<String, Object> claims) {
        // Obtener resource_access del token
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");

        // Si no existe, retornar lista vacía
        if (resourceAccess == null) {
            return List.of();
        }

        // Extraer roles de TODOS los clientes
        // (en caso de que el token tenga roles de múltiples clientes)
        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(client -> (Map<String, Object>) client)
                .filter(client -> client.containsKey("roles"))
                .flatMap(client -> ((List<String>) client.get("roles")).stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}

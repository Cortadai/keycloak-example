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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Configuración de seguridad para API Resource Server.
 *
 * Esta configuración implementa una API REST STATELESS que valida tokens JWT de Keycloak.
 * NO gestiona flujos de autenticación (oauth2Login), solo VALIDA tokens recibidos.
 *
 * Características:
 * - STATELESS: Sin sesiones HTTP, cada request debe incluir el token JWT
 * - Resource Server: Valida tokens JWT contra las claves públicas de Keycloak
 * - Extracción de roles: Convierte roles de Keycloak en authorities de Spring Security
 * - CSRF deshabilitado: API REST sin formularios HTML
 *
 * Flujo de uso:
 * 1. Cliente obtiene token de Keycloak (Client Credentials o Authorization Code)
 * 2. Cliente envía request con header: Authorization: Bearer {token}
 * 3. Spring Security valida el token y extrae roles
 * 4. Los endpoints verifican roles con @PreAuthorize
 *
 * @Configuration - Indica que esta clase contiene configuración de Spring
 * @EnableWebSecurity - Habilita la seguridad web de Spring Security
 * @EnableMethodSecurity - Permite usar anotaciones de seguridad en métodos (@PreAuthorize, etc.)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configuración principal de seguridad para API Resource Server.
     *
     * Define las reglas de autorización y validación de tokens JWT.
     *
     * Reglas de autorización:
     * - /public/** : Acceso público sin token
     * - /api/user/** : Requiere token JWT con rol USER
     * - /api/admin/** : Requiere token JWT con rol ADMIN
     * - Todo lo demás: Requiere token JWT válido
     *
     * @param http El objeto HttpSecurity para configurar la seguridad
     * @return La cadena de filtros de seguridad configurada
     * @throws Exception Si hay algún error en la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Configuración de autorización de peticiones
            .authorizeHttpRequests(auth -> auth
                // Permitir acceso público a estos endpoints (sin autenticación)
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/", "/error").permitAll()

                // Endpoints que requieren el rol USER
                .requestMatchers("/api/user/**").hasRole("USER")

                // Endpoints que requieren el rol ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )

            // Configuración de Resource Server (para validar tokens JWT)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Usar nuestro convertidor personalizado para extraer roles
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // Configuración de sesiones
            // STATELESS = No crear sesiones HTTP (cada request debe incluir el token)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Deshabilitar CSRF para APIs REST
            // CSRF no es necesario en APIs STATELESS que usan tokens JWT
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Convertidor de JWT a Authentication.
     *
     * Este método extrae los roles del token JWT y los convierte en
     * autoridades (GrantedAuthority) que Spring Security puede entender.
     *
     * Los tokens de Keycloak vienen con los roles en dos lugares:
     * 1. realm_access.roles - Roles del realm
     * 2. resource_access.{client-id}.roles - Roles específicos del cliente
     *
     * @return El convertidor configurado
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Convertidor por defecto que extrae scopes del token
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Crear el convertidor principal
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        // Establecer cómo extraer las autoridades (roles)
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extraer roles del realm
            Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt.getClaims());

            // Extraer roles del cliente
            Collection<GrantedAuthority> clientRoles = extractClientRoles(jwt.getClaims());

            // Extraer scopes (usando el convertidor por defecto)
            Collection<GrantedAuthority> scopes = grantedAuthoritiesConverter.convert(jwt);

            // Combinar todos los roles y scopes
            return Stream.of(realmRoles, clientRoles, scopes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        });

        return jwtAuthenticationConverter;
    }

    /**
     * Extrae los roles del realm desde el token JWT.
     *
     * En el token, los roles del realm están en:
     * {
     *   "realm_access": {
     *     "roles": ["role1", "role2"]
     *   }
     * }
     *
     * @param claims Los claims del token JWT
     * @return Lista de autoridades con prefijo ROLE_
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Map<String, Object> claims) {
        Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");

        if (realmAccess == null || realmAccess.get("roles") == null) {
            return List.of();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }

    /**
     * Extrae los roles del cliente desde el token JWT.
     *
     * En el token, los roles del cliente están en:
     * {
     *   "resource_access": {
     *     "spring-boot-client": {
     *       "roles": ["role1", "role2"]
     *     }
     *   }
     * }
     *
     * @param claims Los claims del token JWT
     * @return Lista de autoridades con prefijo ROLE_
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractClientRoles(Map<String, Object> claims) {
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");

        if (resourceAccess == null) {
            return List.of();
        }

        // Extraer roles de todos los clientes
        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(client -> (Map<String, Object>) client)
                .filter(client -> client.containsKey("roles"))
                .flatMap(client -> ((List<String>) client.get("roles")).stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}

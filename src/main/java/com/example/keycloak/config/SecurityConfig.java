package com.example.keycloak.config;

import com.example.keycloak.filter.JwtCookieFilter;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
 * Configuración de seguridad para la aplicación con patrón BFF.
 *
 * Esta clase configura cómo Spring Security debe proteger los endpoints
 * y cómo debe validar los tokens JWT que vienen de Keycloak.
 *
 * Características BFF implementadas:
 * - OAuth2 Login con cookies HttpOnly
 * - CORS para Angular (localhost:4200)
 * - Sesiones STATEFUL para cookies
 * - JwtCookieFilter para extraer JWT de cookies
 * - Soporte dual: cookies (BFF) y headers (API)
 *
 * @Configuration - Indica que esta clase contiene configuración de Spring
 * @EnableWebSecurity - Habilita la seguridad web de Spring Security
 * @EnableMethodSecurity - Permite usar anotaciones de seguridad en métodos (@PreAuthorize, etc.)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final JwtCookieFilter jwtCookieFilter;

    public SecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          JwtCookieFilter jwtCookieFilter) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.jwtCookieFilter = jwtCookieFilter;
    }

    /**
     * Configuración principal de seguridad para el patrón BFF.
     *
     * SecurityFilterChain define las reglas de seguridad para las peticiones HTTP.
     *
     * @param http El objeto HttpSecurity para configurar la seguridad
     * @return La cadena de filtros de seguridad configurada
     * @throws Exception Si hay algún error en la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS: Permitir peticiones desde Angular
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Configuración de autorización de peticiones
            .authorizeHttpRequests(auth -> auth
                // Permitir acceso público a estos endpoints (sin autenticación)
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/", "/error").permitAll()

                // Permitir endpoints de autenticación BFF sin autenticación previa
                .requestMatchers("/api/auth/login", "/api/auth/status").permitAll()
                .requestMatchers("/oauth2/**", "/login/**").permitAll()

                // Endpoints que requieren el rol USER
                .requestMatchers("/api/user/**").hasRole("USER")

                // Endpoints que requieren el rol ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )

            // Configuración de OAuth2 Login (para aplicaciones web con UI)
            .oauth2Login(oauth2 -> oauth2
                // Handler personalizado que crea la cookie HttpOnly
                .successHandler(oauth2LoginSuccessHandler)
            )

            // Configuración de Resource Server (para validar tokens JWT)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Usar nuestro convertidor personalizado para extraer roles
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // Configuración de sesiones
            // STATEFUL = Crear sesiones HTTP para cookies (patrón BFF)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // Añadir filtro personalizado para extraer JWT de cookies
            .addFilterBefore(jwtCookieFilter, UsernamePasswordAuthenticationFilter.class)

            // Deshabilitar CSRF temporalmente para desarrollo
            // TODO: Habilitar en producción con configuración adecuada
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Configuración de CORS para permitir peticiones desde Angular.
     *
     * IMPORTANTE para el patrón BFF:
     * - allowCredentials(true) es CRÍTICO para enviar cookies
     * - allowedOrigins debe ser específico (no "*")
     * - En producción, usar el dominio real de Angular
     *
     * @return Configuración de CORS
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir peticiones desde Angular (localhost:4200 en desarrollo)
        // También permitir null para testing local con archivos HTML
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
                "Set-Cookie"
        ));

        // CRÍTICO: Permite el envío de cookies
        configuration.setAllowCredentials(true);

        // Tiempo de cache de la configuración CORS (1 hora)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/oauth2/**", configuration);
        source.registerCorsConfiguration("/login/**", configuration);

        return source;
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

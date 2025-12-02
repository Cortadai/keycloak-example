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
 * Configuración de seguridad para la aplicación con patrón BFF + Headers.
 *
 * Esta versión implementa:
 * - OAuth2 Login para flujo inicial (callback)
 * - Bearer tokens en Authorization header (STATELESS)
 * - CORS sin credentials (no más cookies)
 * - Resource Server para validación JWT
 *
 * Cambios respecto a la versión con cookies:
 * - SessionCreationPolicy.STATELESS (no sesiones HTTP)
 * - Sin JwtCookieFilter (Spring Security maneja Bearer nativo)
 * - CORS sin allowCredentials
 * - Nuevos endpoints públicos: /exchange, /refresh
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;

    public SecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    /**
     * Configuración principal de seguridad STATELESS con Bearer tokens.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS configurado para headers (sin credentials)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Configuración de autorización de peticiones
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/", "/error").permitAll()

                // Endpoints de autenticación BFF (sin auth previa)
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/exchange").permitAll()
                .requestMatchers("/api/auth/refresh").permitAll()
                // /status requiere token para que Spring valide el JWT automáticamente

                // OAuth2 flow endpoints
                .requestMatchers("/oauth2/**", "/login/**").permitAll()

                // Endpoints que requieren el rol USER
                .requestMatchers("/api/user/**").hasRole("USER")

                // Endpoints que requieren el rol ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )

            // OAuth2 Login solo para el flujo inicial (redirect a Keycloak)
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oauth2LoginSuccessHandler)
            )

            // Resource Server para validar Bearer tokens
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // STATELESS: No crear sesiones HTTP (tokens en cada petición)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Deshabilitar CSRF (no necesario con STATELESS + Bearer tokens)
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Configuración de CORS para Bearer tokens.
     *
     * Sin allowCredentials porque ya no enviamos cookies.
     * Esto simplifica la configuración y permite "*" en origins si se desea.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origins permitidos (en producción usar dominios específicos)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:*",
                "http://127.0.0.1:*"
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

        // Headers expuestos al navegador
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization"
        ));

        // NO permitir credentials (no cookies)
        configuration.setAllowCredentials(false);

        // Cache de CORS (1 hora)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/oauth2/**", configuration);
        source.registerCorsConfiguration("/login/**", configuration);

        return source;
    }

    /**
     * Convertidor de JWT a Authentication.
     * Extrae roles de Keycloak (realm_access y resource_access).
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extraer roles del realm
            Collection<GrantedAuthority> realmRoles = extractRealmRoles(jwt.getClaims());

            // Extraer roles del cliente
            Collection<GrantedAuthority> clientRoles = extractClientRoles(jwt.getClaims());

            // Extraer scopes
            Collection<GrantedAuthority> scopes = grantedAuthoritiesConverter.convert(jwt);

            // Combinar todos
            return Stream.of(realmRoles, clientRoles, scopes)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
        });

        return jwtAuthenticationConverter;
    }

    /**
     * Extrae roles del realm desde realm_access.roles
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
     * Extrae roles del cliente desde resource_access.{client}.roles
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractClientRoles(Map<String, Object> claims) {
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");

        if (resourceAccess == null) {
            return List.of();
        }

        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(client -> (Map<String, Object>) client)
                .filter(client -> client.containsKey("roles"))
                .flatMap(client -> ((List<String>) client.get("roles")).stream())
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                .collect(Collectors.toList());
    }
}

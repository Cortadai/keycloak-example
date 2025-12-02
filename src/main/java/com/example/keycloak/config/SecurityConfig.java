package com.example.keycloak.config;

import com.example.keycloak.filter.FingerprintValidationFilter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuración de seguridad para la aplicación con patrón BFF + Binding.
 *
 * Esta versión implementa:
 * - OAuth2 Login para flujo inicial (callback)
 * - Bearer tokens en Authorization header (STATELESS)
 * - FingerprintValidationFilter para validar binding JWT+Cookie
 * - JWT propio firmado con HMAC (no Keycloak directamente)
 * - CORS con credentials para cookies
 * - Resource Server con decoder personalizado
 *
 * El binding protege contra:
 * - XSS: Atacante roba JWT pero NO tiene cookie HttpOnly → BLOQUEADO
 * - CSRF: Atacante tiene cookie pero NO puede leer JWT → BLOQUEADO
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final FingerprintValidationFilter fingerprintValidationFilter;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public SecurityConfig(OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
                          FingerprintValidationFilter fingerprintValidationFilter) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.fingerprintValidationFilter = fingerprintValidationFilter;
    }

    /**
     * Configuración principal de seguridad STATELESS con Bearer tokens y binding.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS configurado para binding (CON credentials para cookies)
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
                // /status y /logout requieren token para validación

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

            // Resource Server para validar Bearer tokens (JWT propio, no Keycloak)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(customJwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )

            // FingerprintValidationFilter ANTES del filtro JWT
            .addFilterBefore(fingerprintValidationFilter, UsernamePasswordAuthenticationFilter.class)

            // STATELESS: No crear sesiones HTTP (tokens en cada petición)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Deshabilitar CSRF (no necesario con STATELESS + Bearer tokens)
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Configuración de CORS para Bearer tokens CON binding.
     *
     * Con allowCredentials: true para que las cookies viajen automáticamente.
     * IMPORTANTE: No se puede usar "*" con credentials, debe especificar origin exacto.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Origin exacto del frontend (obligatorio con credentials)
        configuration.setAllowedOrigins(Collections.singletonList(frontendUrl));

        // Métodos HTTP permitidos
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // Headers permitidos
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Cookie"
        ));

        // Headers expuestos al navegador
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Set-Cookie"
        ));

        // PERMITIR credentials (cookies viajan automáticamente)
        configuration.setAllowCredentials(true);

        // Cache de CORS (1 hora)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        source.registerCorsConfiguration("/oauth2/**", configuration);
        source.registerCorsConfiguration("/login/**", configuration);

        return source;
    }

    /**
     * Decoder personalizado para JWT propio (firmado con HMAC, no Keycloak).
     * Spring Security usará este decoder para validar los Bearer tokens.
     */
    @Bean
    public JwtDecoder customJwtDecoder() {
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return token -> {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // Convertir Claims de JJWT a Jwt de Spring Security
                Map<String, Object> headers = new HashMap<>();
                headers.put("alg", "HS256");
                headers.put("typ", "JWT");

                Map<String, Object> claimsMap = new HashMap<>();
                claimsMap.put("sub", claims.getSubject());
                claimsMap.put("preferred_username", claims.get("preferred_username"));
                claimsMap.put("email", claims.get("email"));
                claimsMap.put("name", claims.get("name"));
                claimsMap.put("roles", claims.get("roles"));
                claimsMap.put("fingerprint", claims.get("fingerprint"));
                claimsMap.put("iat", claims.getIssuedAt());
                claimsMap.put("exp", claims.getExpiration());

                Instant issuedAt = claims.getIssuedAt() != null
                        ? claims.getIssuedAt().toInstant()
                        : Instant.now();
                Instant expiresAt = claims.getExpiration() != null
                        ? claims.getExpiration().toInstant()
                        : Instant.now().plusSeconds(900);

                return new Jwt(
                        token,
                        issuedAt,
                        expiresAt,
                        headers,
                        claimsMap
                );
            } catch (Exception e) {
                throw new JwtException("Token inválido: " + e.getMessage(), e);
            }
        };
    }

    /**
     * Convertidor de JWT a Authentication.
     * Extrae roles del claim "roles" de nuestro JWT propio.
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();

        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();

            // Extraer roles del claim "roles" de nuestro JWT
            Object rolesObj = jwt.getClaim("roles");
            if (rolesObj instanceof List<?> roles) {
                for (Object role : roles) {
                    if (role instanceof String roleStr) {
                        // Agregar con prefijo ROLE_ para Spring Security
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleStr.toUpperCase()));
                    }
                }
            }

            return authorities;
        });

        return jwtAuthenticationConverter;
    }
}

package com.example.keycloak.controller;

import com.example.keycloak.model.UserInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador con endpoints protegidos para usuarios autenticados.
 *
 * Estos endpoints requieren que el usuario esté autenticado y tenga el rol USER.
 *
 * Para probar estos endpoints necesitas:
 * 1. Obtener un token de Keycloak
 * 2. Enviarlo en el header: Authorization: Bearer {token}
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    /**
     * Obtiene la información del usuario autenticado actual.
     *
     * Este endpoint extrae la información del token JWT.
     *
     * Prueba con Postman:
     * GET http://localhost:8081/api/user/me
     * Header: Authorization: Bearer {tu-token-aqui}
     *
     * @param authentication El objeto de autenticación que contiene el token
     * @return Información del usuario
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public UserInfo getCurrentUser(Authentication authentication) {
        // Obtener el token JWT
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        // Extraer información del token
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        // Extraer roles
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Construir y retornar el objeto UserInfo
        return UserInfo.builder()
                .username(username != null ? username : "unknown")
                .email(email != null ? email : "no-email")
                .name(name != null ? name : "No Name")
                .roles(roles)
                .authenticated(true)
                .message("Información del usuario autenticado")
                .build();
    }

    /**
     * Endpoint de ejemplo para usuarios.
     *
     * Demuestra un endpoint simple que requiere autenticación con rol USER.
     *
     * @param authentication El objeto de autenticación
     * @return Mensaje personalizado
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> dashboard(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bienvenido al dashboard de usuario");
        response.put("user", authentication.getName());
        response.put("info", "Este endpoint requiere estar autenticado con el rol USER");
        return response;
    }

    /**
     * Endpoint que muestra todos los datos del token JWT.
     *
     * Útil para debugging y entender qué información trae el token.
     *
     * @param authentication El objeto de autenticación
     * @return Todos los claims del token JWT
     */
    @GetMapping("/token-info")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> getTokenInfo(Authentication authentication) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Información completa del token JWT");
        response.put("claims", jwt.getClaims());
        response.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));

        return response;
    }

    /**
     * Endpoint que simula obtener datos de perfil del usuario.
     *
     * En una aplicación real, aquí consultarías una base de datos.
     *
     * @param authentication El objeto de autenticación
     * @return Datos simulados del perfil
     */
    @GetMapping("/profile")
    @PreAuthorize("hasRole('USER')")
    public Map<String, Object> getProfile(Authentication authentication) {
        Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", jwt.getClaimAsString("preferred_username"));
        profile.put("email", jwt.getClaimAsString("email"));
        profile.put("name", jwt.getClaimAsString("name"));
        profile.put("emailVerified", jwt.getClaimAsString("email_verified"));

        // Información adicional simulada
        profile.put("memberSince", "2024-01-01");
        profile.put("lastLogin", "2024-11-22");
        profile.put("preferences", Map.of(
                "language", "es",
                "timezone", "UTC-5",
                "notifications", true
        ));

        return profile;
    }
}

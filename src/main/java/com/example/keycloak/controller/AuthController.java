package com.example.keycloak.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para gestionar la autenticación en SPA con PKCE.
 *
 * A diferencia del patrón BFF, en SPA+PKCE:
 * - El frontend gestiona el flujo de autenticación directamente con Keycloak
 * - El backend SOLO valida tokens JWT que vienen en el header Authorization
 * - NO hay endpoints de login/logout/callback (el SPA los gestiona)
 *
 * Este controlador solo proporciona:
 * - /api/auth/status → Verifica si hay un token válido
 *
 * El SPA (Angular/React/Vue) es responsable de:
 * - Iniciar el flujo Authorization Code + PKCE con Keycloak
 * - Almacenar el token en localStorage/sessionStorage
 * - Enviar el token en cada petición (header Authorization)
 * - Renovar el token cuando expira (usando refresh_token)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    /**
     * Verifica si el usuario tiene un token JWT válido.
     *
     * El SPA llama a este endpoint para saber si el token que tiene
     * almacenado sigue siendo válido.
     *
     * GET http://localhost:8081/api/auth/status
     * Header: Authorization: Bearer {token}
     *
     * @return Estado de la autenticación
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAuthStatus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getName().equals("anonymousUser")) {

            response.put("authenticated", true);
            response.put("username", authentication.getName());
            response.put("authorities", authentication.getAuthorities());

            logger.debug("Usuario autenticado: " + authentication.getName());
            return ResponseEntity.ok(response);
        }

        response.put("authenticated", false);
        response.put("message", "No hay token válido o el token ha expirado");

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint informativo sobre el flujo de autenticación.
     *
     * Proporciona información útil para desarrolladores sobre cómo
     * debe funcionar la autenticación en el SPA.
     *
     * GET http://localhost:8081/api/auth/info
     *
     * @return Información sobre el flujo de autenticación
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getAuthInfo() {
        Map<String, Object> info = new HashMap<>();

        info.put("pattern", "SPA with Authorization Code + PKCE");
        info.put("description", "El SPA gestiona la autenticación directamente con Keycloak");

        Map<String, String> flow = new HashMap<>();
        flow.put("1", "SPA inicia Authorization Code Flow con PKCE en Keycloak");
        flow.put("2", "Usuario se autentica en Keycloak");
        flow.put("3", "Keycloak devuelve code al SPA");
        flow.put("4", "SPA intercambia code por tokens (access_token + refresh_token)");
        flow.put("5", "SPA almacena tokens en localStorage/sessionStorage");
        flow.put("6", "SPA envía token en header Authorization en cada petición");
        flow.put("7", "Backend valida token y permite acceso");

        info.put("flow", flow);

        Map<String, String> keycloakEndpoints = new HashMap<>();
        keycloakEndpoints.put("authorization", "http://localhost:9090/realms/mi-realm/protocol/openid-connect/auth");
        keycloakEndpoints.put("token", "http://localhost:9090/realms/mi-realm/protocol/openid-connect/token");
        keycloakEndpoints.put("userinfo", "http://localhost:9090/realms/mi-realm/protocol/openid-connect/userinfo");
        keycloakEndpoints.put("logout", "http://localhost:9090/realms/mi-realm/protocol/openid-connect/logout");

        info.put("keycloak_endpoints", keycloakEndpoints);

        Map<String, String> security = new HashMap<>();
        security.put("token_storage", "localStorage o sessionStorage (accesible desde JavaScript)");
        security.put("xss_protection", "IMPORTANTE: Proteger contra XSS (sanitizar inputs)");
        security.put("pkce", "PKCE protege el flujo Authorization Code en clientes públicos");
        security.put("note", "Menos seguro que BFF pero más simple de implementar");

        info.put("security_notes", security);

        return ResponseEntity.ok(info);
    }
}

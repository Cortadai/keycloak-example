package com.example.keycloak.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador para gestionar la autenticación en el patrón BFF.
 *
 * Este controlador proporciona los endpoints necesarios para que
 * Angular gestione la autenticación sin exponer el JWT:
 *
 * - /api/auth/login  → Inicia el flujo OAuth2 con Keycloak
 * - /api/auth/logout → Cierra sesión e invalida la cookie
 * - /api/auth/status → Verifica si hay una sesión activa
 *
 * Estos endpoints son la capa BFF que protege el JWT del frontend.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    /**
     * Inicia el flujo de autenticación OAuth2 con Keycloak.
     *
     * Cuando Angular llama a este endpoint, Spring Security automáticamente:
     * 1. Redirige al usuario a la página de login de Keycloak
     * 2. El usuario se autentica en Keycloak
     * 3. Keycloak redirige de vuelta con un authorization code
     * 4. Spring Boot intercambia el code por un JWT
     * 5. OAuth2LoginSuccessHandler crea la cookie HttpOnly
     * 6. Redirige al usuario a Angular /dashboard
     *
     * GET http://localhost:8081/api/auth/login
     *
     * Respuesta: Redirect 302 a Keycloak
     */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        logger.info("Iniciando flujo de login OAuth2");

        // Spring Security se encarga automáticamente de la redirección
        // Solo necesitamos redirigir al endpoint de OAuth2
        response.sendRedirect("/oauth2/authorization/keycloak");
    }

    /**
     * Cierra la sesión del usuario.
     *
     * Este endpoint:
     * 1. Invalida la cookie ACCESS_TOKEN
     * 2. Limpia el SecurityContext de Spring Security
     * 3. (Opcional) Revoca el token en Keycloak
     *
     * Llamada desde Angular:
     * POST http://localhost:8081/api/auth/logout
     * (con credentials para enviar la cookie)
     *
     * @param request La petición HTTP
     * @param response La respuesta HTTP
     * @return Mensaje de confirmación
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request,
                                                       HttpServletResponse response) {
        logger.info("Procesando logout de usuario");

        // Obtener la autenticación actual
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            // Limpiar el contexto de seguridad
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            logger.info("SecurityContext limpiado para usuario: " + authentication.getName());
        }

        // Invalidar la cookie creando una nueva con MaxAge=0
        Cookie cookie = new Cookie("ACCESS_TOKEN", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // Expira inmediatamente
        response.addCookie(cookie);

        logger.info("Cookie ACCESS_TOKEN invalidada");

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("message", "Logout exitoso");
        responseBody.put("redirect", frontendUrl + "/login");

        return ResponseEntity.ok(responseBody);
    }

    /**
     * Verifica si el usuario tiene una sesión activa.
     *
     * Angular llama a este endpoint para saber si debe mostrar
     * contenido autenticado o redirigir al login.
     *
     * GET http://localhost:8081/api/auth/status
     * (con credentials para enviar la cookie)
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
        response.put("message", "No hay sesión activa");

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint opcional para obtener la URL de logout de Keycloak.
     *
     * Permite hacer un "logout global" que cierra sesión en Keycloak
     * (SSO logout - cierra sesión en todas las aplicaciones).
     *
     * GET http://localhost:8081/api/auth/logout-url
     *
     * @return URL de logout de Keycloak
     */
    @GetMapping("/logout-url")
    public ResponseEntity<Map<String, String>> getLogoutUrl() {
        try {
            ClientRegistration clientRegistration =
                    clientRegistrationRepository.findByRegistrationId("keycloak");

            if (clientRegistration != null) {
                String logoutUrl = clientRegistration
                        .getProviderDetails()
                        .getConfigurationMetadata()
                        .get("end_session_endpoint")
                        .toString();

                // Añadir redirect después del logout
                String fullLogoutUrl = logoutUrl +
                        "?post_logout_redirect_uri=" + frontendUrl +
                        "&client_id=" + clientRegistration.getClientId();

                Map<String, String> response = new HashMap<>();
                response.put("logoutUrl", fullLogoutUrl);

                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("Error obteniendo URL de logout de Keycloak", e);
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "No se pudo obtener la URL de logout"));
    }

    /**
     * Endpoint de callback después del login (opcional).
     *
     * Este endpoint puede ser útil para logging o analytics.
     * El OAuth2LoginSuccessHandler ya maneja la redirección,
     * pero este endpoint queda disponible si se necesita.
     */
    @GetMapping("/callback")
    public void loginCallback(HttpServletResponse response) throws IOException {
        logger.info("Callback de login recibido");
        // Redirigir al dashboard (ya con cookie creada por el handler)
        response.sendRedirect(frontendUrl + "/dashboard");
    }
}

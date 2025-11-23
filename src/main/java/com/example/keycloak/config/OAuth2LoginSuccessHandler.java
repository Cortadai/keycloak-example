package com.example.keycloak.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handler que se ejecuta después de un login exitoso con OAuth2.
 *
 * Responsabilidades:
 * 1. Obtener el access token del usuario autenticado
 * 2. Crear una cookie HttpOnly con el token
 * 3. Redirigir al usuario al frontend Angular
 *
 * Este es el componente clave del patrón BFF:
 * - El JWT nunca llega al frontend JavaScript
 * - Se almacena de forma segura en una cookie HttpOnly
 * - El navegador la envía automáticamente en cada petición
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @Value("${app.cookie.max-age:3600}")
    private int cookieMaxAge;

    /**
     * Se ejecuta automáticamente después de un login exitoso.
     *
     * @param request La petición HTTP
     * @param response La respuesta HTTP
     * @param authentication El objeto de autenticación con los datos del usuario
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // Verificar que es una autenticación OAuth2
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;

        // Obtener el token JWT del usuario OIDC
        Object principal = oauth2Token.getPrincipal();
        String accessToken = null;

        if (principal instanceof OidcUser) {
            OidcUser oidcUser = (OidcUser) principal;
            // El token ID de OIDC contiene la información del usuario
            accessToken = oidcUser.getIdToken().getTokenValue();
        }

        // Si tenemos el token, crear la cookie
        if (accessToken != null) {
            Cookie cookie = createSecureCookie("ACCESS_TOKEN", accessToken);
            response.addCookie(cookie);

            logger.info("Cookie de sesión creada exitosamente para usuario: " + oauth2Token.getName());
        } else {
            logger.warn("No se pudo obtener el access token para crear la cookie");
        }

        // Redirigir al frontend Angular
        String redirectUrl = frontendUrl + "/dashboard";
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    /**
     * Crea una cookie segura con las mejores prácticas de seguridad.
     *
     * Configuración de seguridad:
     * - HttpOnly: true → JavaScript no puede acceder (previene XSS)
     * - Secure: true (prod) → Solo se envía por HTTPS
     * - SameSite: Strict → Previene CSRF
     * - Path: / → Disponible en toda la aplicación
     * - MaxAge: configurable → Expira junto con el token
     *
     * @param name Nombre de la cookie
     * @param value Valor (el JWT)
     * @param response La respuesta HTTP para configurar headers adicionales
     * @return Cookie configurada
     */
    private Cookie createSecureCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);

        // HttpOnly: previene acceso desde JavaScript (XSS protection)
        cookie.setHttpOnly(true);

        // Secure: solo enviar por HTTPS (en producción)
        cookie.setSecure(secureCookie);

        // Path: disponible en toda la aplicación
        cookie.setPath("/");

        // MaxAge: tiempo de vida en segundos
        cookie.setMaxAge(cookieMaxAge);

        // SameSite: Strict (previene CSRF)
        // En Spring Boot 3.x, SameSite se configura mediante setAttribute
        cookie.setAttribute("SameSite", "Strict");

        return cookie;
    }
}

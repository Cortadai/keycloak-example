package com.example.keycloak.filter;

import com.example.keycloak.service.FingerprintService;
import com.example.keycloak.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filtro que valida el binding entre JWT y cookie de fingerprint.
 *
 * Este filtro se ejecuta ANTES del filtro JWT de Spring Security.
 * Verifica que hash(fingerprint_del_jwt) == cookie Fingerprint.
 *
 * Si el binding es inválido, rechaza la petición con 401.
 * Si el binding es válido, deja pasar a Spring Security para validar el JWT completo.
 *
 * Endpoints excluidos (públicos):
 * - /api/auth/login
 * - /api/auth/exchange
 * - /api/auth/refresh (tiene su propia validación de binding)
 * - /oauth2/**
 * - /login/**
 * - /public/**
 * - / (raíz)
 * - /error
 */
@Component
public class FingerprintValidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintValidationFilter.class);

    private final FingerprintService fingerprintService;
    private final JwtService jwtService;

    @Value("${app.fingerprint.cookie-name:Fingerprint}")
    private String cookieName;

    // Endpoints que no requieren validación de binding
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/auth/login",
            "/api/auth/exchange",
            "/api/auth/refresh",  // Tiene su propia validación
            "/oauth2/",
            "/login/",
            "/public/",
            "/error"
    );

    public FingerprintValidationFilter(FingerprintService fingerprintService, JwtService jwtService) {
        this.fingerprintService = fingerprintService;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Saltar validación para endpoints públicos/excluidos
        if (shouldSkipValidation(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Obtener Authorization header
        String authHeader = request.getHeader("Authorization");

        // Si no hay token, dejar que Spring Security maneje (puede ser endpoint público)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        logger.debug("Validando binding para: {}", path);

        // Extraer fingerprint del JWT
        String fingerprint;
        try {
            fingerprint = jwtService.extractFingerprint(token);
        } catch (Exception e) {
            logger.warn("Error extrayendo fingerprint del JWT: {}", e.getMessage());
            sendUnauthorized(response, "Token inválido");
            return;
        }

        // Obtener cookie de fingerprint
        String cookieHash = extractCookieValue(request, cookieName);

        if (cookieHash == null) {
            logger.warn("Binding incompleto: JWT presente pero cookie '{}' ausente", cookieName);
            sendUnauthorized(response, "Binding requerido - cookie faltante");
            return;
        }

        // Validar binding: hash(fingerprint) == cookie
        if (!fingerprintService.validateFingerprint(fingerprint, cookieHash)) {
            logger.warn("BINDING INVÁLIDO: hash no coincide (posible token robado)");
            sendUnauthorized(response, "Binding inválido");
            return;
        }

        logger.debug("Binding válido para: {}", path);

        // Binding válido, continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }

    /**
     * Verifica si el path debe excluirse de la validación de binding.
     */
    private boolean shouldSkipValidation(String path) {
        // Raíz
        if ("/".equals(path)) {
            return true;
        }

        // Paths excluidos
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extrae el valor de una cookie por nombre.
     */
    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    /**
     * Envía respuesta 401 Unauthorized.
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\"}", message));
    }
}

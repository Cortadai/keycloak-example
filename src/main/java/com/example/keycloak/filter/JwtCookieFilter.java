package com.example.keycloak.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Filtro que extrae el JWT de las cookies y lo convierte en un header Authorization.
 *
 * ¿Por qué es necesario este filtro?
 * - Spring Security espera el JWT en el header: Authorization: Bearer {token}
 * - El patrón BFF almacena el JWT en una cookie HttpOnly
 * - Este filtro hace la "traducción" de cookie → header
 *
 * Flujo:
 * 1. Petición llega con cookie ACCESS_TOKEN
 * 2. Este filtro extrae el JWT de la cookie
 * 3. Añade header: Authorization: Bearer {jwt}
 * 4. Spring Security procesa el request normalmente
 *
 * Ventajas:
 * - No modificamos SecurityConfig existente
 * - Reutilizamos toda la validación JWT ya implementada
 * - Soportamos tanto cookies (BFF) como headers (API directa)
 */
@Component
public class JwtCookieFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtCookieFilter.class);
    private static final String COOKIE_NAME = "ACCESS_TOKEN";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Procesa cada petición HTTP una sola vez.
     *
     * @param request La petición HTTP
     * @param response La respuesta HTTP
     * @param filterChain La cadena de filtros
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // Si ya tiene Authorization header, no hacer nada
        // Esto permite usar tanto cookies como headers
        if (request.getHeader(AUTH_HEADER) != null) {
            logger.debug("Authorization header ya presente, saltando cookie filter");
            filterChain.doFilter(request, response);
            return;
        }

        // Buscar la cookie con el JWT
        String jwtFromCookie = extractJwtFromCookie(request);

        if (jwtFromCookie != null) {
            logger.debug("JWT encontrado en cookie, añadiendo Authorization header");

            // Crear un request wrapper que incluye el header Authorization
            HttpServletRequest wrappedRequest = new JwtHeaderRequestWrapper(request, jwtFromCookie);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            // No hay cookie, continuar normalmente
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Extrae el JWT de la cookie ACCESS_TOKEN.
     *
     * @param request La petición HTTP
     * @return El JWT o null si no existe
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * Wrapper que añade el header Authorization al request.
     *
     * Spring Security leerá este header y validará el JWT normalmente.
     */
    private static class JwtHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String jwt;

        public JwtHeaderRequestWrapper(HttpServletRequest request, String jwt) {
            super(request);
            this.jwt = jwt;
        }

        @Override
        public String getHeader(String name) {
            // Si piden el header Authorization, devolver nuestro JWT
            if (AUTH_HEADER.equalsIgnoreCase(name)) {
                return BEARER_PREFIX + jwt;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            // Si piden el header Authorization, devolver nuestro JWT
            if (AUTH_HEADER.equalsIgnoreCase(name)) {
                List<String> values = new ArrayList<>();
                values.add(BEARER_PREFIX + jwt);
                return Collections.enumeration(values);
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            // Añadir Authorization a la lista de headers
            Set<String> headerNames = new HashSet<>();
            Enumeration<String> originalHeaders = super.getHeaderNames();

            while (originalHeaders.hasMoreElements()) {
                headerNames.add(originalHeaders.nextElement());
            }

            headerNames.add(AUTH_HEADER);
            return Collections.enumeration(headerNames);
        }
    }
}

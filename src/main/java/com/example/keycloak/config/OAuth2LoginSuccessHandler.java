package com.example.keycloak.config;

import com.example.keycloak.model.TokenData;
import com.example.keycloak.service.TokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Handler que se ejecuta después de un login exitoso con OAuth2.
 *
 * Versión BFF con Headers (no cookies):
 * 1. Obtiene access token y refresh token de Keycloak
 * 2. Genera código temporal UUID y lo almacena en Redis (TTL 30s)
 * 3. Redirige al frontend con el código temporal en query param
 *
 * El frontend luego intercambia el código temporal por el accessToken
 * mediante el endpoint /api/auth/exchange
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final TokenService tokenService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public OAuth2LoginSuccessHandler(TokenService tokenService,
                                     OAuth2AuthorizedClientService authorizedClientService) {
        this.tokenService = tokenService;
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Se ejecuta automáticamente después de un login exitoso.
     * Genera código temporal y redirige al frontend.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔐 [AUTH FLOW] PASO 2: Login OAuth2 exitoso en Keycloak");

        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            log.warn("   ⚠️ Tipo de autenticación inesperado: {}", authentication.getClass().getSimpleName());
            super.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();

        log.info("   → Proveedor OAuth2: {}", registrationId);
        log.info("   → Usuario autenticado: {}", oauth2Token.getName());

        // Obtener el cliente autorizado para acceder a los tokens
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                registrationId,
                oauth2Token.getName()
        );

        if (authorizedClient == null) {
            log.error("   ❌ No se pudo obtener el cliente autorizado");
            log.info("═══════════════════════════════════════════════════════════════");
            response.sendRedirect(frontendUrl + "/login?error=auth_failed");
            return;
        }

        // Obtener tokens
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        if (accessToken == null) {
            log.error("   ❌ No se pudo obtener el access token de Keycloak");
            log.info("═══════════════════════════════════════════════════════════════");
            response.sendRedirect(frontendUrl + "/login?error=no_token");
            return;
        }

        log.info("   ✅ Tokens recibidos de Keycloak:");
        log.info("      • AccessToken: {}...", accessToken.getTokenValue().substring(0, 20));
        log.info("      • RefreshToken: {}", refreshToken != null ? "presente" : "no proporcionado");

        // Extraer información del usuario del Access Token de Keycloak
        String userId = extractUserId(oauth2Token);
        String username = extractUsername(oauth2Token);
        String email = extractEmail(oauth2Token);
        String name = extractNameFromAccessToken(accessToken.getTokenValue());
        // Extraer roles del Access Token de Keycloak (no del ID Token)
        List<String> roles = extractRolesFromAccessToken(accessToken.getTokenValue());

        // Calcular expiresIn
        long expiresIn = calculateExpiresIn(accessToken);

        log.info("   → UserId (sub claim): {}", userId);
        log.info("   → Username: {}", username);
        log.info("   → Email: {}", email);
        log.info("   → Name: {}", name);
        log.info("   → Roles: {}", roles);
        log.info("   → Token expira en: {} segundos", expiresIn);

        // Crear datos del token para Redis (incluye info para JWT propio)
        TokenData tokenData = new TokenData();
        tokenData.setAccessToken(accessToken.getTokenValue());
        tokenData.setRefreshToken(refreshToken != null ? refreshToken.getTokenValue() : null);
        tokenData.setUserId(userId);
        tokenData.setExpiresIn(expiresIn);
        tokenData.setUsername(username);
        tokenData.setEmail(email);
        tokenData.setName(name);
        tokenData.setRoles(roles);

        // Generar código temporal y almacenar en Redis (TTL 30s)
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("📦 [AUTH FLOW] PASO 3: Generando código temporal");
        String tempCode = tokenService.createTempCode(tokenData);
        log.info("   ✅ Código temporal creado: {}...", tempCode.substring(0, 8));
        log.info("   → Almacenado en Redis con TTL: 30 segundos");
        log.info("   → Contenido: accessToken, refreshToken, userId, expiresIn");

        // Redirigir al frontend con el código temporal
        String redirectUrl = frontendUrl + "/callback?code=" + tempCode;
        log.info("   → Redirigiendo al frontend: {}", redirectUrl);
        log.info("═══════════════════════════════════════════════════════════════");

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    /**
     * Extrae el userId (claim 'sub') del token de autenticación.
     */
    private String extractUserId(OAuth2AuthenticationToken oauth2Token) {
        Object principal = oauth2Token.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            // El claim 'sub' es el identificador único del usuario
            return oidcUser.getSubject();
        }

        // Fallback al nombre de la autenticación
        return oauth2Token.getName();
    }

    /**
     * Extrae el username (preferred_username) del token.
     */
    private String extractUsername(OAuth2AuthenticationToken oauth2Token) {
        Object principal = oauth2Token.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            String username = oidcUser.getPreferredUsername();
            if (username != null) {
                return username;
            }
        }

        return oauth2Token.getName();
    }

    /**
     * Extrae el email del token.
     */
    private String extractEmail(OAuth2AuthenticationToken oauth2Token) {
        Object principal = oauth2Token.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getEmail();
        }

        return null;
    }

    /**
     * Extrae el nombre completo (name) decodificando el Access Token de Keycloak.
     *
     * @param accessTokenValue El valor del access token JWT
     * @return Nombre completo del usuario o null si no existe
     */
    private String extractNameFromAccessToken(String accessTokenValue) {
        try {
            String[] parts = accessTokenValue.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> claims = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});

            return (String) claims.get("name");
        } catch (Exception e) {
            log.warn("   ⚠️ Error extrayendo name del Access Token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extrae los roles decodificando el Access Token de Keycloak (JWT).
     * El Access Token contiene realm_access.roles y resource_access.{client}.roles
     * que NO están presentes en el ID Token por defecto.
     *
     * @param accessTokenValue El valor del access token JWT
     * @return Lista de roles extraídos
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromAccessToken(String accessTokenValue) {
        List<String> roles = new ArrayList<>();

        try {
            // El JWT tiene 3 partes separadas por punto: header.payload.signature
            String[] parts = accessTokenValue.split("\\.");
            if (parts.length != 3) {
                log.warn("   ⚠️ Access Token no tiene formato JWT válido");
                return roles;
            }

            // Decodificar el payload (segunda parte)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> claims = mapper.readValue(payload, new TypeReference<Map<String, Object>>() {});

            log.debug("   → Claims del Access Token: {}", claims.keySet());

            // Extraer roles del realm (realm_access.roles)
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get("realm_access");
            if (realmAccess != null && realmAccess.get("roles") != null) {
                List<String> realmRoles = (List<String>) realmAccess.get("roles");
                // Filtrar roles internos de Keycloak
                realmRoles.stream()
                        .filter(role -> !role.startsWith("default-roles-"))
                        .filter(role -> !role.equals("offline_access"))
                        .filter(role -> !role.equals("uma_authorization"))
                        .forEach(roles::add);
                log.debug("   → Roles del realm: {}", realmRoles);
            }

            // Extraer roles del cliente (resource_access.{client}.roles)
            Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
            if (resourceAccess != null) {
                for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
                    if (entry.getValue() instanceof Map) {
                        Map<String, Object> clientAccess = (Map<String, Object>) entry.getValue();
                        if (clientAccess.get("roles") instanceof List) {
                            List<String> clientRoles = (List<String>) clientAccess.get("roles");
                            roles.addAll(clientRoles);
                            log.debug("   → Roles del cliente '{}': {}", entry.getKey(), clientRoles);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("   ❌ Error decodificando Access Token para extraer roles: {}", e.getMessage());
        }

        return roles;
    }

    /**
     * Calcula los segundos hasta que expire el token.
     */
    private long calculateExpiresIn(OAuth2AccessToken accessToken) {
        Instant expiresAt = accessToken.getExpiresAt();

        if (expiresAt != null) {
            long seconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
            return Math.max(0, seconds);
        }

        // Valor por defecto si no hay información de expiración
        return 300; // 5 minutos
    }
}

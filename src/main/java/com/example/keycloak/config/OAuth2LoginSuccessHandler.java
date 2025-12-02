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

import java.io.IOException;
import java.time.Instant;

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

        // Extraer userId del principal (claim 'sub')
        String userId = extractUserId(oauth2Token);

        // Calcular expiresIn
        long expiresIn = calculateExpiresIn(accessToken);

        log.info("   → UserId (sub claim): {}", userId);
        log.info("   → Token expira en: {} segundos", expiresIn);

        // Crear datos del token para Redis
        TokenData tokenData = new TokenData(
                accessToken.getTokenValue(),
                refreshToken != null ? refreshToken.getTokenValue() : null,
                userId,
                expiresIn
        );

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

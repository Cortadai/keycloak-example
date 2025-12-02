package com.example.keycloak.controller;

import com.example.keycloak.dto.AuthStatusResponse;
import com.example.keycloak.dto.ExchangeRequest;
import com.example.keycloak.dto.LogoutResponse;
import com.example.keycloak.dto.TokenResponse;
import com.example.keycloak.model.TokenData;
import com.example.keycloak.service.KeycloakTokenService;
import com.example.keycloak.service.TokenService;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.ParseException;
import java.util.Optional;

/**
 * Controlador para gestionar la autenticación en el patrón BFF con Headers.
 *
 * Endpoints:
 * - GET  /api/auth/login    → Inicia flujo OAuth2 con Keycloak
 * - POST /api/auth/exchange → Intercambia código temporal por accessToken
 * - POST /api/auth/refresh  → Renueva accessToken usando refreshToken de Redis
 * - POST /api/auth/logout   → Cierra sesión, revoca tokens
 * - GET  /api/auth/status   → Verifica validez del Bearer token
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final TokenService tokenService;
    private final KeycloakTokenService keycloakTokenService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public AuthController(TokenService tokenService, KeycloakTokenService keycloakTokenService) {
        this.tokenService = tokenService;
        this.keycloakTokenService = keycloakTokenService;
    }

    /**
     * Inicia el flujo de autenticación OAuth2 con Keycloak.
     * Redirige al endpoint de Spring Security que maneja OAuth2.
     */
    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🚀 [AUTH FLOW] PASO 1: Iniciando flujo OAuth2");
        logger.info("   → Redirigiendo a: /oauth2/authorization/keycloak");
        logger.info("   → Spring Security manejará el redirect a Keycloak");
        logger.info("═══════════════════════════════════════════════════════════════");
        response.sendRedirect("/oauth2/authorization/keycloak");
    }

    /**
     * Intercambia un código temporal por el accessToken.
     *
     * Este endpoint es llamado por el frontend después de recibir
     * el código temporal en el callback (/callback?code=xxx).
     *
     * El código temporal:
     * - Tiene TTL de 30 segundos
     * - Solo se puede usar una vez (se elimina de Redis)
     * - Contiene accessToken, refreshToken y userId
     *
     * @param request DTO con el código temporal
     * @return accessToken y expiresIn
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody ExchangeRequest request) {
        String code = request.getCode();

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔄 [AUTH FLOW] PASO 4: Intercambiando código temporal por JWT");
        logger.info("   → Código recibido: {}...", code != null ? code.substring(0, Math.min(8, code.length())) : "null");

        if (code == null || code.isBlank()) {
            logger.warn("   ❌ Código vacío o nulo");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.badRequest()
                    .body(new LogoutResponse(false, "Código temporal requerido"));
        }

        logger.info("   → Buscando código en Redis...");
        Optional<TokenData> tokenDataOpt = tokenService.exchangeTempCode(code);

        if (tokenDataOpt.isEmpty()) {
            logger.warn("   ❌ Código no encontrado en Redis (expirado o ya usado)");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Código temporal inválido o expirado"));
        }

        TokenData tokenData = tokenDataOpt.get();
        logger.info("   ✅ Código válido encontrado");
        logger.info("   → UserId: {}", tokenData.getUserId());
        logger.info("   → AccessToken: {}...", tokenData.getAccessToken().substring(0, 20));
        logger.info("   → ExpiresIn: {} segundos", tokenData.getExpiresIn());

        // Almacenar refresh token en Redis para este usuario
        if (tokenData.getRefreshToken() != null) {
            logger.info("   → Almacenando RefreshToken en Redis para usuario: {}", tokenData.getUserId());
            tokenService.storeRefreshToken(tokenData.getUserId(), tokenData.getRefreshToken());
            logger.info("   ✅ RefreshToken almacenado (TTL: 8 horas)");
        }

        // Devolver solo el accessToken (nunca el refreshToken)
        TokenResponse response = new TokenResponse(
                tokenData.getAccessToken(),
                tokenData.getExpiresIn()
        );

        logger.info("   🎉 Intercambio completado exitosamente");
        logger.info("   → Frontend recibirá: accessToken + expiresIn");
        logger.info("   → RefreshToken: NUNCA se envía al frontend (seguro en Redis)");
        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(response);
    }

    /**
     * Renueva el accessToken usando el refreshToken almacenado en Redis.
     *
     * El frontend envía el token expirado en el header Authorization.
     * El backend:
     * 1. Valida la firma del JWT (ignora expiración)
     * 2. Extrae el userId del claim 'sub'
     * 3. Busca el refreshToken en Redis
     * 4. Llama a Keycloak para obtener nuevos tokens
     * 5. Actualiza el refreshToken en Redis
     * 6. Devuelve el nuevo accessToken
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔃 [AUTH FLOW] Refresh de Token solicitado");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("   ❌ No se recibió header Authorization");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Token requerido para refresh"));
        }

        String expiredToken = authHeader.substring(7);
        logger.info("   → Token recibido: {}...", expiredToken.substring(0, Math.min(20, expiredToken.length())));

        // Extraer userId del token (validar firma, ignorar expiración)
        String userId;
        try {
            userId = extractUserIdFromToken(expiredToken);
            logger.info("   → UserId extraído del token: {}", userId);
        } catch (Exception e) {
            logger.warn("   ❌ Error extrayendo userId: {}", e.getMessage());
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Token inválido"));
        }

        // Buscar refresh token en Redis
        logger.info("   → Buscando RefreshToken en Redis para userId: {}", userId);
        Optional<String> refreshTokenOpt = tokenService.getRefreshToken(userId);

        if (refreshTokenOpt.isEmpty()) {
            logger.warn("   ❌ No hay RefreshToken en Redis (sesión no existe o expiró)");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Sesión expirada, inicie sesión nuevamente"));
        }

        logger.info("   ✅ RefreshToken encontrado en Redis");
        logger.info("   → Solicitando nuevo AccessToken a Keycloak...");

        // Llamar a Keycloak para refrescar el token
        Optional<TokenResponse> newTokenOpt = keycloakTokenService.refreshAccessToken(refreshTokenOpt.get());

        if (newTokenOpt.isEmpty()) {
            // El refresh token fue revocado o expiró en Keycloak
            tokenService.deleteRefreshToken(userId);
            logger.warn("   ❌ Keycloak rechazó el RefreshToken (revocado o expirado)");
            logger.info("   → RefreshToken eliminado de Redis");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Sesión expirada, inicie sesión nuevamente"));
        }

        TokenResponse newToken = newTokenOpt.get();
        logger.info("   ✅ Keycloak devolvió nuevo AccessToken");

        // Actualizar refresh token en Redis si Keycloak devolvió uno nuevo
        if (newToken.getNewRefreshToken() != null) {
            tokenService.storeRefreshToken(userId, newToken.getNewRefreshToken());
            logger.info("   → Nuevo RefreshToken almacenado en Redis");
        }

        logger.info("   🎉 Refresh completado exitosamente para usuario: {}", userId);
        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(new TokenResponse(newToken.getAccessToken(), newToken.getExpiresIn()));
    }

    /**
     * Cierra la sesión del usuario.
     *
     * 1. Extrae userId del token
     * 2. Obtiene refresh token de Redis
     * 3. Revoca el token en Keycloak (opcional)
     * 4. Elimina refresh token de Redis
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🚪 [AUTH FLOW] Logout solicitado");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("   → No hay token, logout considerado exitoso");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.ok(new LogoutResponse(true, "Logout exitoso"));
        }

        String token = authHeader.substring(7);

        try {
            String userId = extractUserIdFromToken(token);
            logger.info("   → UserId: {}", userId);

            // Obtener refresh token para revocarlo en Keycloak
            Optional<String> refreshTokenOpt = tokenService.getRefreshToken(userId);

            if (refreshTokenOpt.isPresent()) {
                logger.info("   → Revocando token en Keycloak...");
                keycloakTokenService.revokeToken(refreshTokenOpt.get());
                logger.info("   ✅ Token revocado en Keycloak");

                logger.info("   → Eliminando RefreshToken de Redis...");
                tokenService.deleteRefreshToken(userId);
                logger.info("   ✅ RefreshToken eliminado de Redis");

                logger.info("   🎉 Logout completo para usuario: {}", userId);
            } else {
                logger.info("   → No había RefreshToken en Redis (ya estaba deslogueado)");
            }

        } catch (Exception e) {
            logger.warn("   ⚠️ Error durante logout: {}", e.getMessage());
            logger.info("   → El frontend limpiará su estado local de todas formas");
        }

        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(new LogoutResponse(true, "Logout exitoso"));
    }

    /**
     * Verifica si el Bearer token es válido.
     *
     * Si el token está en el header y es válido, Spring Security
     * ya lo habrá autenticado antes de llegar aquí.
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getAuthStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        logger.info("───────────────────────────────────────────────────────────────");
        logger.info("🔍 [AUTH STATUS] Verificación de token");

        // Si hay un token válido, Spring Security ya lo procesó
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt) {

            Jwt jwt = (Jwt) authentication.getPrincipal();
            String username = jwt.getClaimAsString("preferred_username");
            String userId = jwt.getSubject();

            logger.info("   ✅ Token VÁLIDO");
            logger.info("   → Username: {}", username);
            logger.info("   → UserId: {}", userId);
            logger.info("   → Expira: {}", jwt.getExpiresAt());
            logger.info("───────────────────────────────────────────────────────────────");
            return ResponseEntity.ok(new AuthStatusResponse(true, username, "Token válido"));
        }

        // También verificar si el header tiene un token que podemos validar manualmente
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            logger.warn("   ❌ Token presente pero NO válido (Spring Security lo rechazó)");
        } else {
            logger.info("   → No se recibió token en el header");
        }

        logger.info("───────────────────────────────────────────────────────────────");
        return ResponseEntity.ok(new AuthStatusResponse(false, null, "No autenticado"));
    }

    /**
     * Extrae el userId (claim 'sub') de un JWT.
     * Valida la firma pero permite tokens expirados (para refresh).
     */
    private String extractUserIdFromToken(String token) throws ParseException {
        // Usar nimbus-jose-jwt para parsear sin validar expiración
        JWT jwt = JWTParser.parse(token);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Token sin claim 'sub'");
        }

        return subject;
    }
}

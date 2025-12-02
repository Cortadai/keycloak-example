package com.example.keycloak.controller;

import com.example.keycloak.dto.AuthStatusResponse;
import com.example.keycloak.dto.ExchangeRequest;
import com.example.keycloak.dto.LogoutResponse;
import com.example.keycloak.dto.TokenResponse;
import com.example.keycloak.model.TokenData;
import com.example.keycloak.service.FingerprintService;
import com.example.keycloak.service.JwtService;
import com.example.keycloak.service.KeycloakTokenService;
import com.example.keycloak.service.TokenService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador para gestionar la autenticación en el patrón BFF con Binding.
 *
 * Esta versión implementa el patrón "Llave Partida":
 * - JWT en localStorage contiene claim "fingerprint"
 * - Cookie HttpOnly contiene SHA-256(fingerprint)
 * - Para autenticarse se necesitan AMBOS: JWT + Cookie
 *
 * Protecciones:
 * - XSS: Atacante roba JWT pero NO tiene la cookie HttpOnly → BLOQUEADO
 * - CSRF: Atacante tiene cookie pero NO puede leer JWT de localStorage → BLOQUEADO
 *
 * Endpoints:
 * - GET  /api/auth/login    → Inicia flujo OAuth2 con Keycloak
 * - POST /api/auth/exchange → Intercambia código temporal por JWT con fingerprint
 * - POST /api/auth/refresh  → Renueva JWT, rota fingerprint
 * - POST /api/auth/logout   → Cierra sesión, elimina cookie
 * - GET  /api/auth/status   → Verifica validez del binding (JWT + Cookie)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final TokenService tokenService;
    private final KeycloakTokenService keycloakTokenService;
    private final FingerprintService fingerprintService;
    private final JwtService jwtService;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${app.fingerprint.cookie-name:Fingerprint}")
    private String cookieName;

    @Value("${app.fingerprint.http-only:true}")
    private boolean cookieHttpOnly;

    @Value("${app.fingerprint.secure:false}")
    private boolean cookieSecure;

    @Value("${app.fingerprint.same-site:Strict}")
    private String cookieSameSite;

    @Value("${app.fingerprint.path:/}")
    private String cookiePath;

    @Value("${app.fingerprint.max-age:900}")
    private int cookieMaxAge;

    public AuthController(TokenService tokenService,
                          KeycloakTokenService keycloakTokenService,
                          FingerprintService fingerprintService,
                          JwtService jwtService) {
        this.tokenService = tokenService;
        this.keycloakTokenService = keycloakTokenService;
        this.fingerprintService = fingerprintService;
        this.jwtService = jwtService;
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
     * Intercambia un código temporal por el JWT con fingerprint.
     *
     * Este endpoint es llamado por el frontend después de recibir
     * el código temporal en el callback (/callback?code=xxx).
     *
     * Proceso:
     * 1. Recupera TokenData de Redis usando el código temporal
     * 2. Genera fingerprint aleatorio (UUID)
     * 3. Calcula hash SHA-256 del fingerprint
     * 4. Crea JWT propio con claim "fingerprint"
     * 5. Almacena refresh token de Keycloak en Redis
     * 6. Establece cookie HttpOnly con el hash
     * 7. Devuelve JWT al frontend
     *
     * @param request DTO con el código temporal
     * @param response para establecer la cookie
     * @return JWT con fingerprint y expiresIn
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchangeCode(@RequestBody ExchangeRequest request,
                                          HttpServletResponse response) {
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
        logger.info("   → Username: {}", tokenData.getUsername());

        // Almacenar refresh token de Keycloak en Redis para este usuario
        if (tokenData.getRefreshToken() != null) {
            logger.info("   → Almacenando RefreshToken de Keycloak en Redis...");
            tokenService.storeRefreshToken(tokenData.getUserId(), tokenData.getRefreshToken());
            logger.info("   ✅ RefreshToken almacenado (TTL: 8 horas)");
        }

        // Generar fingerprint y hash para binding
        logger.info("   → Generando fingerprint para binding...");
        String fingerprint = fingerprintService.generateFingerprint();
        String fingerprintHash = fingerprintService.hashFingerprint(fingerprint);
        logger.info("   ✅ Fingerprint generado: {}...", fingerprint.substring(0, 8));
        logger.info("   ✅ Hash SHA-256: {}...", fingerprintHash.substring(0, 16));

        // Crear JWT propio con fingerprint
        logger.info("   → Generando JWT propio con claim fingerprint...");
        String jwt = jwtService.generateToken(
                tokenData.getUserId(),
                tokenData.getUsername(),
                tokenData.getEmail(),
                tokenData.getName(),
                tokenData.getRoles() != null ? tokenData.getRoles() : List.of(),
                fingerprint
        );
        logger.info("   ✅ JWT generado: {}...", jwt.substring(0, 20));

        // Establecer cookie HttpOnly con el hash del fingerprint
        setFingerprintCookie(response, fingerprintHash);
        logger.info("   ✅ Cookie '{}' establecida (HttpOnly, TTL: {}s)", cookieName, cookieMaxAge);

        // Devolver JWT al frontend
        TokenResponse tokenResponse = new TokenResponse(jwt, jwtService.getExpirationSeconds());

        logger.info("   🎉 Intercambio con binding completado exitosamente");
        logger.info("   → Frontend recibirá: JWT con fingerprint + expiresIn");
        logger.info("   → Cookie HttpOnly: hash del fingerprint (automática)");
        logger.info("   → RefreshToken Keycloak: seguro en Redis (nunca expuesto)");
        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * Renueva el JWT usando el refreshToken almacenado en Redis.
     * Rota el fingerprint en cada refresh (mayor seguridad).
     *
     * Proceso:
     * 1. Extrae fingerprint del JWT expirado
     * 2. Valida que hash(fingerprint) == cookie
     * 3. Extrae userId del JWT
     * 4. Obtiene refreshToken de Redis
     * 5. Llama a Keycloak para nuevos tokens
     * 6. Genera NUEVO fingerprint (rotación)
     * 7. Crea nuevo JWT con nuevo fingerprint
     * 8. Actualiza cookie con nuevo hash
     * 9. Devuelve nuevo JWT
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = "Fingerprint", required = false) String cookieHash,
            HttpServletResponse response) {

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔃 [AUTH FLOW] Refresh de Token con Binding solicitado");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("   ❌ No se recibió header Authorization");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Token requerido para refresh"));
        }

        if (cookieHash == null || cookieHash.isBlank()) {
            logger.warn("   ❌ No se recibió cookie Fingerprint (binding faltante)");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Binding requerido para refresh"));
        }

        String expiredToken = authHeader.substring(7);
        logger.info("   → Token recibido: {}...", expiredToken.substring(0, Math.min(20, expiredToken.length())));
        logger.info("   → Cookie hash recibido: {}...", cookieHash.substring(0, Math.min(16, cookieHash.length())));

        // Validar binding: hash(fingerprint_del_jwt) == cookie
        String fingerprint;
        String userId;
        try {
            fingerprint = jwtService.extractFingerprint(expiredToken);
            userId = jwtService.extractUserId(expiredToken);
            logger.info("   → Fingerprint extraído del JWT: {}...", fingerprint.substring(0, 8));
            logger.info("   → UserId extraído del JWT: {}", userId);
        } catch (Exception e) {
            logger.warn("   ❌ Error extrayendo datos del JWT: {}", e.getMessage());
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Token inválido"));
        }

        // Validar binding
        if (!fingerprintService.validateFingerprint(fingerprint, cookieHash)) {
            logger.warn("   ❌ BINDING INVÁLIDO - hash no coincide (posible token robado)");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Binding inválido"));
        }
        logger.info("   ✅ Binding válido (fingerprint coincide con cookie)");

        // Buscar refresh token de Keycloak en Redis
        logger.info("   → Buscando RefreshToken en Redis para userId: {}", userId);
        Optional<String> refreshTokenOpt = tokenService.getRefreshToken(userId);

        if (refreshTokenOpt.isEmpty()) {
            logger.warn("   ❌ No hay RefreshToken en Redis (sesión no existe o expiró)");
            clearFingerprintCookie(response);
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Sesión expirada, inicie sesión nuevamente"));
        }

        logger.info("   ✅ RefreshToken de Keycloak encontrado en Redis");
        logger.info("   → Solicitando nuevos tokens a Keycloak...");

        // Llamar a Keycloak para refrescar el token
        Optional<TokenResponse> newKeycloakTokenOpt = keycloakTokenService.refreshAccessToken(refreshTokenOpt.get());

        if (newKeycloakTokenOpt.isEmpty()) {
            // El refresh token fue revocado o expiró en Keycloak
            tokenService.deleteRefreshToken(userId);
            clearFingerprintCookie(response);
            logger.warn("   ❌ Keycloak rechazó el RefreshToken (revocado o expirado)");
            logger.info("   → RefreshToken eliminado de Redis, Cookie eliminada");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LogoutResponse(false, "Sesión expirada, inicie sesión nuevamente"));
        }

        TokenResponse keycloakResponse = newKeycloakTokenOpt.get();
        logger.info("   ✅ Keycloak devolvió nuevos tokens");

        // Actualizar refresh token en Redis si Keycloak devolvió uno nuevo
        if (keycloakResponse.getNewRefreshToken() != null) {
            tokenService.storeRefreshToken(userId, keycloakResponse.getNewRefreshToken());
            logger.info("   → Nuevo RefreshToken de Keycloak almacenado en Redis");
        }

        // ROTACIÓN: Generar NUEVO fingerprint para mayor seguridad
        logger.info("   → Rotando fingerprint (generando nuevo)...");
        String newFingerprint = fingerprintService.generateFingerprint();
        String newFingerprintHash = fingerprintService.hashFingerprint(newFingerprint);
        logger.info("   ✅ Nuevo fingerprint: {}...", newFingerprint.substring(0, 8));
        logger.info("   ✅ Nuevo hash: {}...", newFingerprintHash.substring(0, 16));

        // Extraer claims del token anterior para recrear el JWT
        Map<String, Object> claims = jwtService.extractAllClaims(expiredToken);

        // Crear nuevo JWT con el nuevo fingerprint
        @SuppressWarnings("unchecked")
        String newJwt = jwtService.generateToken(
                (String) claims.get("userId"),
                (String) claims.get("username"),
                (String) claims.get("email"),
                (String) claims.get("name"),
                (List<String>) claims.get("roles"),
                newFingerprint
        );
        logger.info("   ✅ Nuevo JWT generado con fingerprint rotado");

        // Actualizar cookie con el nuevo hash
        setFingerprintCookie(response, newFingerprintHash);
        logger.info("   ✅ Cookie actualizada con nuevo hash");

        logger.info("   🎉 Refresh con rotación de fingerprint completado para usuario: {}", userId);
        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(new TokenResponse(newJwt, jwtService.getExpirationSeconds()));
    }

    /**
     * Cierra la sesión del usuario.
     *
     * 1. Extrae userId del token (con validación de binding)
     * 2. Obtiene refresh token de Redis
     * 3. Revoca el token en Keycloak (opcional)
     * 4. Elimina refresh token de Redis
     * 5. Elimina cookie de fingerprint
     */
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = "Fingerprint", required = false) String cookieHash,
            HttpServletResponse response) {

        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🚪 [AUTH FLOW] Logout solicitado");

        // Siempre eliminar la cookie, incluso si no hay token
        clearFingerprintCookie(response);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("   → No hay token, logout considerado exitoso");
            logger.info("   → Cookie eliminada");
            logger.info("═══════════════════════════════════════════════════════════════");
            return ResponseEntity.ok(new LogoutResponse(true, "Logout exitoso"));
        }

        String token = authHeader.substring(7);

        try {
            String userId = jwtService.extractUserId(token);
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
            logger.info("   → Cookie eliminada, frontend limpiará su estado");
        }

        logger.info("   ✅ Cookie '{}' eliminada", cookieName);
        logger.info("═══════════════════════════════════════════════════════════════");
        return ResponseEntity.ok(new LogoutResponse(true, "Logout exitoso"));
    }

    /**
     * Verifica si el Bearer token con binding es válido.
     *
     * Para que sea válido debe:
     * 1. Tener JWT válido (firma y no expirado)
     * 2. Tener cookie Fingerprint
     * 3. hash(fingerprint_del_jwt) == cookie
     */
    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> getAuthStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = "Fingerprint", required = false) String cookieHash) {

        logger.info("───────────────────────────────────────────────────────────────");
        logger.info("🔍 [AUTH STATUS] Verificación de token con binding");

        // Si hay un token válido, Spring Security ya lo procesó
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Jwt) {

            Jwt jwt = (Jwt) authentication.getPrincipal();
            String username = jwt.getClaimAsString("preferred_username");
            String userId = jwt.getSubject();

            // Verificar binding (el filtro ya lo hizo, pero verificamos para el log)
            String fingerprint = jwt.getClaimAsString("fingerprint");
            if (fingerprint != null && cookieHash != null) {
                boolean bindingValid = fingerprintService.validateFingerprint(fingerprint, cookieHash);
                logger.info("   → Binding: {}", bindingValid ? "✅ VÁLIDO" : "❌ INVÁLIDO");
            }

            logger.info("   ✅ Token VÁLIDO con binding");
            logger.info("   → Username: {}", username);
            logger.info("   → UserId: {}", userId);
            logger.info("   → Expira: {}", jwt.getExpiresAt());
            logger.info("───────────────────────────────────────────────────────────────");
            return ResponseEntity.ok(new AuthStatusResponse(true, username, "Token válido con binding"));
        }

        // También verificar si el header tiene un token que podemos validar manualmente
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            if (cookieHash == null) {
                logger.warn("   ❌ Token presente pero SIN cookie (binding incompleto)");
            } else {
                logger.warn("   ❌ Token presente pero NO válido (Spring Security lo rechazó)");
            }
        } else {
            logger.info("   → No se recibió token en el header");
        }

        logger.info("───────────────────────────────────────────────────────────────");
        return ResponseEntity.ok(new AuthStatusResponse(false, null, "No autenticado"));
    }

    /**
     * Establece la cookie HttpOnly con el hash del fingerprint.
     */
    private void setFingerprintCookie(HttpServletResponse response, String fingerprintHash) {
        Cookie cookie = new Cookie(cookieName, fingerprintHash);
        cookie.setHttpOnly(cookieHttpOnly);
        cookie.setSecure(cookieSecure);
        cookie.setPath(cookiePath);
        cookie.setMaxAge(cookieMaxAge);
        // SameSite se configura via header porque Cookie API no lo soporta directamente
        response.addCookie(cookie);

        // Agregar SameSite via header (la Cookie API de Java no lo soporta nativamente)
        String cookieHeader = String.format("%s=%s; Max-Age=%d; Path=%s; %s; %s; SameSite=%s",
                cookieName,
                fingerprintHash,
                cookieMaxAge,
                cookiePath,
                cookieHttpOnly ? "HttpOnly" : "",
                cookieSecure ? "Secure" : "",
                cookieSameSite);
        response.setHeader("Set-Cookie", cookieHeader);
    }

    /**
     * Elimina la cookie de fingerprint (logout).
     */
    private void clearFingerprintCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(cookieHttpOnly);
        cookie.setSecure(cookieSecure);
        cookie.setPath(cookiePath);
        cookie.setMaxAge(0); // Eliminar cookie
        response.addCookie(cookie);

        // También via header para SameSite
        String cookieHeader = String.format("%s=; Max-Age=0; Path=%s; %s; %s; SameSite=%s",
                cookieName,
                cookiePath,
                cookieHttpOnly ? "HttpOnly" : "",
                cookieSecure ? "Secure" : "",
                cookieSameSite);
        response.setHeader("Set-Cookie", cookieHeader);
    }
}

package com.example.keycloak.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Servicio para generar y validar JWT propios con fingerprint.
 *
 * Este servicio NO usa los tokens de Keycloak directamente.
 * En su lugar, crea un JWT nuevo que incluye:
 * - Claims del usuario (sub, preferred_username, email, roles)
 * - Claim "fingerprint" para el binding con la cookie
 *
 * El JWT se firma con HMAC SHA-256 usando un secret configurado.
 */
@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_FINGERPRINT = "fingerprint";
    private static final String CLAIM_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLES = "roles";

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expirationSeconds) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationSeconds * 1000;
        log.info("JwtService inicializado con TTL de {} segundos", expirationSeconds);
    }

    /**
     * Genera un JWT propio con el fingerprint incluido como claim.
     *
     * @param userId identificador del usuario (sub)
     * @param username nombre de usuario preferido
     * @param email email del usuario
     * @param name nombre completo del usuario
     * @param roles lista de roles del usuario
     * @param fingerprint el fingerprint generado para binding
     * @return JWT firmado como String
     */
    public String generateToken(String userId, String username, String email,
                                 String name, List<String> roles, String fingerprint) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .subject(userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_FINGERPRINT, fingerprint)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();

        log.debug("JWT generado para usuario: {}, expira: {}", userId, expiration);
        return token;
    }

    /**
     * Extrae el fingerprint de un JWT.
     * Valida la firma pero permite tokens expirados (para refresh).
     *
     * @param token JWT a procesar
     * @return fingerprint extraído del claim
     * @throws IllegalArgumentException si el token es inválido o no tiene fingerprint
     */
    public String extractFingerprint(String token) {
        Claims claims = parseClaimsAllowExpired(token);
        String fingerprint = claims.get(CLAIM_FINGERPRINT, String.class);

        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("Token sin claim fingerprint");
        }

        return fingerprint;
    }

    /**
     * Extrae el userId (subject) de un JWT.
     * Valida la firma pero permite tokens expirados (para refresh).
     *
     * @param token JWT a procesar
     * @return userId extraído del subject
     */
    public String extractUserId(String token) {
        Claims claims = parseClaimsAllowExpired(token);
        String userId = claims.getSubject();

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Token sin subject (userId)");
        }

        return userId;
    }

    /**
     * Valida un JWT completamente (firma y expiración).
     *
     * @param token JWT a validar
     * @return true si el token es válido, false en caso contrario
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene el tiempo de expiración configurado en segundos.
     *
     * @return tiempo de expiración en segundos
     */
    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    /**
     * Parsea los claims de un JWT permitiendo tokens expirados.
     * Esto es necesario para el flujo de refresh donde el token ya expiró.
     *
     * @param token JWT a parsear
     * @return Claims del token
     * @throws IllegalArgumentException si el token tiene firma inválida
     */
    private Claims parseClaimsAllowExpired(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // Token expirado pero firma válida - devolver claims igualmente
            log.debug("Token expirado pero firma válida, devolviendo claims");
            return e.getClaims();
        } catch (Exception e) {
            log.warn("Error parseando token: {}", e.getMessage());
            throw new IllegalArgumentException("Token inválido: " + e.getMessage(), e);
        }
    }

    /**
     * Extrae todos los claims de un JWT para reconstruirlo durante refresh.
     *
     * @param token JWT a procesar
     * @return Map con los claims extraídos
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractAllClaims(String token) {
        Claims claims = parseClaimsAllowExpired(token);
        return Map.of(
                "userId", claims.getSubject(),
                "username", claims.get(CLAIM_USERNAME, String.class),
                "email", claims.get(CLAIM_EMAIL, String.class),
                "name", claims.get(CLAIM_NAME, String.class) != null ? claims.get(CLAIM_NAME, String.class) : "",
                "roles", claims.get(CLAIM_ROLES, List.class)
        );
    }
}

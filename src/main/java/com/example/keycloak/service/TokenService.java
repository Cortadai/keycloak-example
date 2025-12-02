package com.example.keycloak.service;

import com.example.keycloak.model.TokenData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Servicio para gestionar tokens en Redis.
 *
 * Implementa:
 * - Códigos temporales de intercambio (TTL 30s, uso único)
 * - Refresh tokens por usuario (TTL 8h, sesión única por usuario)
 * - Degradación elegante cuando Redis no está disponible
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final String TEMP_CODE_PREFIX = "temp_code:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final long TEMP_CODE_TTL_SECONDS = 30;
    private static final long REFRESH_TOKEN_TTL_HOURS = 8;

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Genera un código temporal y almacena los datos del token en Redis.
     * El código temporal tiene un TTL de 30 segundos y es de uso único.
     *
     * @param tokenData datos del token (accessToken, refreshToken, userId, expiresIn)
     * @return código temporal UUID para el intercambio
     */
    public String createTempCode(TokenData tokenData) {
        String code = UUID.randomUUID().toString();
        String key = TEMP_CODE_PREFIX + code;

        try {
            redisTemplate.opsForValue().set(key, tokenData, TEMP_CODE_TTL_SECONDS, TimeUnit.SECONDS);
            log.debug("Código temporal creado: {} para usuario: {}", code, tokenData.getUserId());
        } catch (RedisConnectionFailureException e) {
            log.error("Error de conexión con Redis al crear código temporal", e);
            throw new RuntimeException("Servicio de autenticación no disponible temporalmente", e);
        }

        return code;
    }

    /**
     * Intercambia un código temporal por los datos del token.
     * El código se elimina después del intercambio (uso único).
     *
     * @param code código temporal a intercambiar
     * @return Optional con los datos del token, o vacío si el código no existe o expiró
     */
    public Optional<TokenData> exchangeTempCode(String code) {
        String key = TEMP_CODE_PREFIX + code;

        try {
            // Obtener y eliminar atómicamente
            Object data = redisTemplate.opsForValue().getAndDelete(key);

            if (data == null) {
                log.warn("Código temporal no encontrado o expirado: {}", code);
                return Optional.empty();
            }

            if (data instanceof TokenData tokenData) {
                log.debug("Código temporal intercambiado exitosamente para usuario: {}", tokenData.getUserId());
                return Optional.of(tokenData);
            }

            log.error("Tipo de dato inesperado en Redis para código: {}", code);
            return Optional.empty();

        } catch (RedisConnectionFailureException e) {
            log.error("Error de conexión con Redis al intercambiar código temporal", e);
            throw new RuntimeException("Servicio de autenticación no disponible temporalmente", e);
        }
    }

    /**
     * Almacena el refresh token para un usuario.
     * Sobrescribe cualquier refresh token anterior (sesión única por usuario).
     *
     * @param userId identificador del usuario
     * @param refreshToken token de refresco de Keycloak
     */
    public void storeRefreshToken(String userId, String refreshToken) {
        String key = REFRESH_TOKEN_PREFIX + userId;

        try {
            stringRedisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL_HOURS, TimeUnit.HOURS);
            log.debug("Refresh token almacenado para usuario: {}", userId);
        } catch (RedisConnectionFailureException e) {
            log.error("Error de conexión con Redis al almacenar refresh token", e);
            // Degradación elegante: no bloqueamos el flujo principal
        }
    }

    /**
     * Obtiene el refresh token de un usuario.
     *
     * @param userId identificador del usuario
     * @return Optional con el refresh token, o vacío si no existe
     */
    public Optional<String> getRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;

        try {
            String refreshToken = stringRedisTemplate.opsForValue().get(key);
            if (refreshToken != null) {
                log.debug("Refresh token recuperado para usuario: {}", userId);
                return Optional.of(refreshToken);
            }
            log.debug("No se encontró refresh token para usuario: {}", userId);
            return Optional.empty();

        } catch (RedisConnectionFailureException e) {
            log.error("Error de conexión con Redis al obtener refresh token", e);
            return Optional.empty(); // Degradación elegante
        }
    }

    /**
     * Elimina el refresh token de un usuario (logout).
     *
     * @param userId identificador del usuario
     * @return true si se eliminó, false si no existía o hubo error
     */
    public boolean deleteRefreshToken(String userId) {
        String key = REFRESH_TOKEN_PREFIX + userId;

        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Refresh token eliminado para usuario: {}", userId);
                return true;
            }
            log.debug("No había refresh token que eliminar para usuario: {}", userId);
            return false;

        } catch (RedisConnectionFailureException e) {
            log.error("Error de conexión con Redis al eliminar refresh token", e);
            return false; // Degradación elegante
        }
    }

    /**
     * Verifica si Redis está disponible.
     *
     * @return true si Redis responde, false en caso contrario
     */
    public boolean isRedisAvailable() {
        try {
            String pong = stringRedisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.warn("Redis no está disponible: {}", e.getMessage());
            return false;
        }
    }
}

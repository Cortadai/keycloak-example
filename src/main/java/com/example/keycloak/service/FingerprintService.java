package com.example.keycloak.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Servicio para generar y validar fingerprints.
 *
 * Implementa el patrón "Llave Partida" donde:
 * - El fingerprint (UUID aleatorio) se incluye como claim en el JWT
 * - El hash SHA-256 del fingerprint se envía en una cookie HttpOnly
 * - Para autenticarse se necesitan AMBOS: JWT con fingerprint + cookie con hash
 *
 * Protecciones:
 * - XSS: Atacante roba JWT pero NO tiene la cookie HttpOnly → BLOQUEADO
 * - CSRF: Atacante tiene cookie pero NO puede leer JWT de localStorage → BLOQUEADO
 */
@Service
@Slf4j
public class FingerprintService {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Genera un fingerprint aleatorio usando UUID.
     * Criptográficamente seguro gracias a SecureRandom interno de UUID.randomUUID().
     *
     * @return UUID aleatorio como String
     */
    public String generateFingerprint() {
        String fingerprint = UUID.randomUUID().toString();
        log.debug("Fingerprint generado: {}...", fingerprint.substring(0, 8));
        return fingerprint;
    }

    /**
     * Calcula el hash SHA-256 de un fingerprint.
     * El hash se almacena en la cookie HttpOnly.
     *
     * @param fingerprint el fingerprint a hashear
     * @return hash SHA-256 en formato hexadecimal
     */
    public String hashFingerprint(String fingerprint) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(hashBytes);
            log.debug("Hash generado: {}...", hash.substring(0, 16));
            return hash;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en cualquier JVM
            throw new RuntimeException("Algoritmo " + HASH_ALGORITHM + " no disponible", e);
        }
    }

    /**
     * Valida que el hash del fingerprint coincida con el valor esperado.
     *
     * @param fingerprint el fingerprint extraído del JWT
     * @param expectedHash el hash extraído de la cookie
     * @return true si SHA-256(fingerprint) == expectedHash
     */
    public boolean validateFingerprint(String fingerprint, String expectedHash) {
        if (fingerprint == null || expectedHash == null) {
            log.warn("Validación de fingerprint fallida: valores nulos");
            return false;
        }

        String calculatedHash = hashFingerprint(fingerprint);
        boolean valid = calculatedHash.equals(expectedHash);

        if (valid) {
            log.debug("Fingerprint válido");
        } else {
            log.warn("Fingerprint inválido - hash no coincide");
        }

        return valid;
    }
}

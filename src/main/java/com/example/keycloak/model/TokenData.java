package com.example.keycloak.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Datos del token almacenados temporalmente en Redis.
 * Se usa para el intercambio del código temporal por el accessToken.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Token de acceso JWT de Keycloak.
     */
    private String accessToken;

    /**
     * Token de refresco de Keycloak (nunca se envía al frontend).
     */
    private String refreshToken;

    /**
     * Identificador único del usuario (claim 'sub' del JWT).
     */
    private String userId;

    /**
     * Tiempo de expiración del accessToken en segundos.
     */
    private long expiresIn;
}

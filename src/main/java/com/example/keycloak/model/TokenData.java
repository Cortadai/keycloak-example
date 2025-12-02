package com.example.keycloak.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Datos del token almacenados temporalmente en Redis.
 * Se usa para el intercambio del código temporal por el accessToken.
 *
 * Incluye información del usuario extraída de Keycloak para generar
 * el JWT propio con fingerprint (binding).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenData implements Serializable {

    private static final long serialVersionUID = 2L;

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

    /**
     * Nombre de usuario preferido (preferred_username).
     */
    private String username;

    /**
     * Email del usuario.
     */
    private String email;

    /**
     * Nombre completo del usuario (name).
     */
    private String name;

    /**
     * Roles del usuario extraídos de Keycloak.
     */
    private List<String> roles;
}

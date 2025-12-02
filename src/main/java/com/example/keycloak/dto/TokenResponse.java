package com.example.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta con el access token para el frontend.
 * El refresh token NUNCA se incluye en esta respuesta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    /**
     * JWT de acceso para autenticar peticiones.
     */
    @JsonProperty("accessToken")
    private String accessToken;

    /**
     * Tiempo de expiración en segundos.
     */
    @JsonProperty("expiresIn")
    private long expiresIn;

    /**
     * Nuevo refresh token (solo para uso interno, nunca se serializa).
     */
    @JsonIgnore
    private String newRefreshToken;

    public TokenResponse(String accessToken, long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }
}

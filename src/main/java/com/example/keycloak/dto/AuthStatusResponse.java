package com.example.keycloak.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para verificar el estado de autenticación.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthStatusResponse {

    /**
     * Indica si el usuario está autenticado.
     */
    private boolean authenticated;

    /**
     * Nombre de usuario (null si no está autenticado).
     */
    private String username;

    /**
     * Mensaje adicional (útil para debugging).
     */
    private String message;

    public AuthStatusResponse(boolean authenticated, String username) {
        this.authenticated = authenticated;
        this.username = username;
    }
}

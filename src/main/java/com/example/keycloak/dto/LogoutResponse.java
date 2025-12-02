package com.example.keycloak.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de respuesta para el endpoint de logout.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {

    /**
     * Indica si el logout fue exitoso.
     */
    private boolean success;

    /**
     * Mensaje descriptivo del resultado.
     */
    private String message;
}

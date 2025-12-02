package com.example.keycloak.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la solicitud de intercambio de código temporal.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRequest {

    /**
     * Código temporal UUID generado tras el login exitoso.
     */
    private String code;
}

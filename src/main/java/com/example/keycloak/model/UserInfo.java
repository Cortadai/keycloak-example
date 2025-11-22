package com.example.keycloak.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Modelo que representa la información del usuario autenticado.
 *
 * Anotaciones de Lombok:
 * @Data - Genera getters, setters, toString, equals y hashCode
 * @Builder - Permite crear objetos usando el patrón Builder
 * @NoArgsConstructor - Genera constructor sin argumentos
 * @AllArgsConstructor - Genera constructor con todos los argumentos
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /**
     * Nombre de usuario (username)
     */
    private String username;

    /**
     * Email del usuario
     */
    private String email;

    /**
     * Nombre completo del usuario
     */
    private String name;

    /**
     * Roles asignados al usuario
     */
    private List<String> roles;

    /**
     * Indica si el usuario está autenticado
     */
    private boolean authenticated;

    /**
     * Mensaje adicional (para ejemplos)
     */
    private String message;
}

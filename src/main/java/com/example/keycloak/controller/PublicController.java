package com.example.keycloak.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador con endpoints públicos (sin autenticación).
 *
 * Estos endpoints pueden ser accedidos por cualquier persona,
 * sin necesidad de estar autenticado.
 *
 * @RestController - Indica que esta clase es un controlador REST
 *                   (todas las respuestas serán JSON)
 * @RequestMapping - Define la ruta base para todos los endpoints de este controlador
 */
@RestController
@RequestMapping("/public")
public class PublicController {

    /**
     * Endpoint público simple.
     *
     * Prueba con: curl http://localhost:8081/public/hello
     *
     * @return Mensaje de bienvenida
     */
    @GetMapping("/hello")
    public Map<String, String> hello() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "¡Hola! Este es un endpoint público.");
        response.put("info", "No necesitas estar autenticado para ver esto.");
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    /**
     * Endpoint que retorna información sobre la aplicación.
     *
     * Prueba con: curl http://localhost:8081/public/info
     *
     * @return Información de la aplicación
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", "Keycloak Spring Boot Demo");
        response.put("version", "1.0.0");
        response.put("description", "Aplicación de ejemplo para aprender Spring Boot con Keycloak");

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("public", "/public/* - Endpoints públicos (sin autenticación)");
        endpoints.put("user", "/api/user/* - Endpoints para usuarios autenticados (rol USER)");
        endpoints.put("admin", "/api/admin/* - Endpoints para administradores (rol ADMIN)");

        response.put("endpoints", endpoints);
        response.put("keycloak", "http://localhost:9090");

        return response;
    }

    /**
     * Endpoint de estado del servicio.
     *
     * Útil para health checks.
     * Prueba con: curl http://localhost:8081/public/status
     *
     * @return Estado del servicio
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("message", "El servicio está funcionando correctamente");
        return response;
    }
}

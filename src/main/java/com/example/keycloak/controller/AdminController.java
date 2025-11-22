package com.example.keycloak.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Controlador con endpoints solo para administradores.
 *
 * Estos endpoints requieren el rol ADMIN.
 * Solo los usuarios con este rol podrán acceder.
 *
 * Demuestra control de acceso basado en roles (RBAC - Role-Based Access Control).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /**
     * Panel de administración.
     *
     * Solo accesible por usuarios con rol ADMIN.
     *
     * @param authentication El objeto de autenticación
     * @return Información del panel de administración
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> adminDashboard(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Bienvenido al panel de administración");
        response.put("admin", authentication.getName());
        response.put("info", "Este endpoint solo es accesible para usuarios con rol ADMIN");
        response.put("timestamp", LocalDateTime.now().toString());

        // Estadísticas simuladas
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalUsers", 150);
        stats.put("activeUsers", 120);
        stats.put("totalOrders", 500);
        stats.put("pendingOrders", 25);

        response.put("statistics", stats);
        return response;
    }

    /**
     * Lista todos los usuarios (simulado).
     *
     * En una aplicación real, aquí consultarías la base de datos
     * o harías una llamada a la API de administración de Keycloak.
     *
     * @return Lista simulada de usuarios
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listUsers() {
        List<Map<String, Object>> users = new ArrayList<>();

        // Usuarios simulados
        users.add(createUser("juan", "juan@example.com", "Juan Pérez", List.of("USER")));
        users.add(createUser("maria", "maria@example.com", "María García", List.of("USER")));
        users.add(createUser("admin", "admin@example.com", "Admin User", List.of("USER", "ADMIN")));

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Lista de usuarios del sistema");
        response.put("total", users.size());
        response.put("users", users);

        return response;
    }

    /**
     * Obtiene estadísticas del sistema.
     *
     * @return Estadísticas simuladas
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getSystemStats() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Estadísticas del sistema");

        Map<String, Object> systemInfo = new HashMap<>();
        systemInfo.put("uptime", "48 hours");
        systemInfo.put("requests", 15420);
        systemInfo.put("errors", 12);
        systemInfo.put("avgResponseTime", "45ms");

        response.put("system", systemInfo);
        response.put("timestamp", LocalDateTime.now().toString());

        return response;
    }

    /**
     * Endpoint para configuración del sistema.
     *
     * Demuestra cómo proteger operaciones sensibles.
     *
     * @param settings Configuración a actualizar
     * @return Confirmación de actualización
     */
    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> settings) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Configuración actualizada exitosamente");
        response.put("updatedSettings", settings);
        response.put("timestamp", LocalDateTime.now().toString());

        return response;
    }

    /**
     * Endpoint peligroso que solo admin debe poder acceder.
     *
     * Demuestra la importancia del control de acceso.
     *
     * @return Mensaje de confirmación
     */
    @DeleteMapping("/dangerous-operation")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> dangerousOperation() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Operación peligrosa ejecutada");
        response.put("warning", "Este endpoint podría eliminar datos o realizar cambios importantes");
        response.put("note", "Por eso está protegido con @PreAuthorize('hasRole(ADMIN)')");

        return response;
    }

    /**
     * Endpoint que permite múltiples roles.
     *
     * Demuestra cómo permitir acceso a usuarios con cualquiera de varios roles.
     *
     * @param authentication El objeto de autenticación
     * @return Información del usuario
     */
    @GetMapping("/or-super-user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_USER')")
    public Map<String, Object> adminOrSuperUser(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Este endpoint permite ADMIN o SUPER_USER");
        response.put("user", authentication.getName());
        response.put("authorities", authentication.getAuthorities());

        return response;
    }

    /**
     * Método auxiliar para crear objetos de usuario simulados.
     */
    private Map<String, Object> createUser(String username, String email, String name, List<String> roles) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("email", email);
        user.put("name", name);
        user.put("roles", roles);
        user.put("enabled", true);
        return user;
    }
}

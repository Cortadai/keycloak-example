package com.example.keycloak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de la aplicación Spring Boot integrada con Keycloak.
 *
 * @SpringBootApplication es una anotación compuesta que incluye:
 * - @Configuration: Indica que la clase contiene configuración
 * - @EnableAutoConfiguration: Habilita la configuración automática de Spring Boot
 * - @ComponentScan: Busca componentes, servicios, etc. en el paquete actual y subpaquetes
 */
@SpringBootApplication
public class KeycloakDemoApplication {

    /**
     * Punto de entrada de la aplicación.
     *
     * @param args Argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(KeycloakDemoApplication.class, args);
        System.out.println("================================");
        System.out.println("Aplicación iniciada correctamente");
        System.out.println("URL: http://localhost:8081");
        System.out.println("================================");
    }
}

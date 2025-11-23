package com.example.keycloak;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KeycloakDemoApplication.class);

    /**
     * Punto de entrada de la aplicación.
     *
     * @param args Argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(KeycloakDemoApplication.class, args);
        log.info("================================");
        log.info("Aplicación iniciada correctamente");
        log.info("URL: http://localhost:8081");
        log.info("================================");
    }
}

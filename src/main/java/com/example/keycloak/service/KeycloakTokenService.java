package com.example.keycloak.service;

import com.example.keycloak.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * Servicio para interactuar con Keycloak.
 *
 * Proporciona:
 * - Refresh de tokens usando el refresh_token grant
 * - Revocación de tokens en logout
 */
@Service
@Slf4j
public class KeycloakTokenService {

    private final RestTemplate restTemplate;
    private final String tokenUri;
    private final String revokeUri;
    private final String clientId;
    private final String clientSecret;

    public KeycloakTokenService(
            @Value("${spring.security.oauth2.client.provider.keycloak.token-uri}") String tokenUri,
            @Value("${app.keycloak.revoke-uri}") String revokeUri,
            @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret) {
        this.restTemplate = new RestTemplate();
        this.tokenUri = tokenUri;
        this.revokeUri = revokeUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * Refresca un access token usando el refresh token.
     *
     * @param refreshToken el refresh token actual
     * @return Optional con la respuesta de tokens, o vacío si falla
     */
    public Optional<TokenResponse> refreshAccessToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "refresh_token");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("refresh_token", refreshToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenMap = response.getBody();

                TokenResponse tokenResponse = new TokenResponse(
                        (String) tokenMap.get("access_token"),
                        ((Number) tokenMap.get("expires_in")).longValue()
                );

                // El nuevo refresh token para actualizar en Redis
                String newRefreshToken = (String) tokenMap.get("refresh_token");
                tokenResponse.setNewRefreshToken(newRefreshToken);

                log.debug("Token refrescado exitosamente");
                return Optional.of(tokenResponse);
            }

            log.warn("Respuesta inesperada de Keycloak al refrescar token: {}", response.getStatusCode());
            return Optional.empty();

        } catch (RestClientException e) {
            log.error("Error al refrescar token con Keycloak: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Revoca un refresh token en Keycloak.
     * Esto invalida el token en el servidor de autenticación.
     *
     * @param refreshToken el refresh token a revocar
     * @return true si se revocó exitosamente, false en caso contrario
     */
    public boolean revokeToken(String refreshToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("token", refreshToken);
            body.add("token_type_hint", "refresh_token");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                    revokeUri,
                    HttpMethod.POST,
                    request,
                    Void.class
            );

            boolean success = response.getStatusCode().is2xxSuccessful();
            if (success) {
                log.debug("Token revocado exitosamente en Keycloak");
            } else {
                log.warn("Keycloak devolvió código {} al revocar token", response.getStatusCode());
            }
            return success;

        } catch (RestClientException e) {
            log.error("Error al revocar token en Keycloak: {}", e.getMessage());
            return false;
        }
    }
}

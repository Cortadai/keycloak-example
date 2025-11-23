import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * Configuración de la aplicación Angular para SPA con PKCE.
 *
 * Providers críticos:
 * - provideRouter: Rutas de la aplicación
 * - provideHttpClient: Habilita HttpClient para peticiones HTTP
 * - withInterceptors([authInterceptor]): Añade token en Authorization header
 * - provideOAuthClient: Habilita OAuthService para PKCE
 *
 * Diferencias con BFF:
 * - provideOAuthClient: Necesario para angular-oauth2-oidc
 * - NO necesitamos withCredentials (se gestiona en el interceptor si es necesario)
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor])
    ),
    // CRÍTICO: Prover el OAuthClient para usar angular-oauth2-oidc
    provideOAuthClient()
  ]
};

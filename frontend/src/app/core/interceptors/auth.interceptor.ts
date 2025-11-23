import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { OAuthService } from 'angular-oauth2-oidc';

/**
 * Interceptor HTTP para SPA con Authorization Code + PKCE.
 *
 * Responsabilidades:
 * 1. Añadir Authorization header con el access token a todas las peticiones
 * 2. Manejar errores de autenticación (401, 403)
 * 3. Refrescar token automáticamente cuando expira (opcional)
 *
 * Diferencias con el patrón BFF:
 * - NO usa withCredentials (no hay cookies)
 * - Añade header Authorization: Bearer {token}
 * - Token viene de localStorage (gestionado por OAuthService)
 *
 * IMPORTANTE:
 * - El token se obtiene de OAuthService (angular-oauth2-oidc)
 * - Se añade automáticamente a todas las peticiones al backend
 * - Si el token expira (401), se puede refrescar automáticamente
 * - NO inyectamos AuthService para evitar dependencia circular
 *   (AuthService → HttpClient → authInterceptor → AuthService)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const oauthService = inject(OAuthService);
  const router = inject(Router);

  // Obtener el access token actual directamente de OAuthService
  const token = oauthService.getAccessToken();

  // Si hay token, añadirlo al header Authorization
  if (token) {
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Manejar errores de autenticación
      if (error.status === 401) {
        // 401 Unauthorized: Token inválido o expirado

        // Opción 1: Intentar refrescar el token automáticamente
        // oauthService.refreshToken().then(() => {
        //   // Reintentar la petición con el nuevo token
        // }).catch(() => {
        //   // Si falla el refresh, hacer logout
        //   oauthService.logOut();
        //   router.navigate(['/login']);
        // });

        // Opción 2 (simple): Hacer logout directamente
        console.warn('Token expirado o inválido (401), redirigiendo a login');
        oauthService.logOut();
        router.navigate(['/login']);

      } else if (error.status === 403) {
        // 403 Forbidden: Usuario autenticado pero sin permisos
        console.warn('Acceso denegado (403) - Sin permisos suficientes');
        // Opcional: redirigir a página de "acceso denegado"
        // router.navigate(['/access-denied']);

      } else if (error.status === 0) {
        // Error de red o CORS
        console.error('Error de red o CORS:', error);
      }

      // Propagar el error para que lo maneje el componente si quiere
      return throwError(() => error);
    })
  );
};

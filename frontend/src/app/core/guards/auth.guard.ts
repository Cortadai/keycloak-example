import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Guard funcional para proteger rutas que requieren autenticación.
 *
 * Este guard verifica si hay un token JWT válido en localStorage
 * antes de permitir el acceso a rutas protegidas.
 *
 * Diferencias con BFF:
 * - NO consulta al backend (verifica localmente)
 * - Usa OAuthService.hasValidAccessToken()
 * - Más rápido (no hace petición HTTP)
 *
 * Uso en rutas:
 * {
 *   path: 'dashboard',
 *   component: DashboardComponent,
 *   canActivate: [authGuard]
 * }
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Verificar si hay un token válido (síncrono, no hace HTTP request)
  if (authService.isAuthenticatedValue) {
    // Usuario autenticado, permitir acceso
    return true;
  } else {
    // No autenticado, redirigir a login
    console.warn('AuthGuard: Usuario no autenticado, redirigiendo a /login');
    router.navigate(['/login'], {
      queryParams: { returnUrl: state.url }
    });
    return false;
  }
};

/**
 * Guard para verificar roles específicos.
 *
 * Verifica si el usuario tiene un rol específico en el token JWT.
 * Los roles están en el campo realm_access.roles del token.
 *
 * Uso:
 * canActivate: [authGuard, roleGuard('admin')]
 *
 * Nota: Usar rol sin prefijo ROLE_ (ej: 'admin' no 'ROLE_ADMIN')
 */
export function roleGuard(requiredRole: string): CanActivateFn {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    // Verificar autenticación primero
    if (!authService.isAuthenticatedValue) {
      console.warn('RoleGuard: Usuario no autenticado');
      router.navigate(['/login']);
      return false;
    }

    // Verificar rol (síncrono)
    if (authService.hasRole(requiredRole)) {
      return true;
    } else {
      console.warn(`RoleGuard: Usuario no tiene rol "${requiredRole}"`);
      router.navigate(['/access-denied']);
      return false;
    }
  };
}

import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

/**
 * Guard funcional para proteger rutas que requieren autenticación.
 *
 * Este guard verifica con el backend si hay una sesión activa
 * antes de permitir el acceso a rutas protegidas.
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

  // Verificar estado de autenticación con el backend
  return authService.checkAuthStatus().pipe(
    map(status => {
      if (status.authenticated) {
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
    }),
    catchError(() => {
      // Error al verificar estado, asumir no autenticado
      console.error('AuthGuard: Error verificando autenticación');
      router.navigate(['/login']);
      return of(false);
    })
  );
};

/**
 * Guard para verificar roles específicos.
 *
 * Uso:
 * canActivate: [authGuard, roleGuard('ROLE_ADMIN')]
 */
export function roleGuard(requiredRole: string): CanActivateFn {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.hasRole(requiredRole).pipe(
      map(hasRole => {
        if (hasRole) {
          return true;
        } else {
          console.warn(`RoleGuard: Usuario no tiene rol ${requiredRole}`);
          router.navigate(['/access-denied']);
          return false;
        }
      }),
      catchError(() => {
        router.navigate(['/login']);
        return of(false);
      })
    );
  };
}

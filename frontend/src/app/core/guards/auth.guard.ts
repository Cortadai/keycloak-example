import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { map, catchError, of } from 'rxjs';

/**
 * Prefijo para logs del guard
 */
const LOG_PREFIX = '🛡️ [AuthGuard]';

/**
 * Guard funcional para proteger rutas que requieren autenticación.
 *
 * Verificación en dos pasos:
 * 1. Primero verifica si hay token en localStorage (rápido, sin HTTP)
 * 2. Si hay token, verifica con backend que siga siendo válido
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

  console.log('───────────────────────────────────────────────────────────────');
  console.log(`${LOG_PREFIX} Verificando acceso a: ${state.url}`);

  // Verificación rápida: ¿hay token en localStorage?
  const token = authService.getStoredToken();

  if (!token) {
    // No hay token, redirigir a login
    console.log(`   → No hay token en localStorage`);
    console.log(`   ❌ Acceso DENEGADO - Redirigiendo a /login`);
    console.log('───────────────────────────────────────────────────────────────');
    router.navigate(['/login'], {
      queryParams: { returnUrl: state.url }
    });
    return of(false);
  }

  console.log(`   → Token encontrado: ${token.substring(0, 15)}...`);

  // Verificación de expiración local
  if (authService.isTokenExpired()) {
    console.log(`   → Token EXPIRADO localmente`);
    console.log(`   → Intentando refresh antes de bloquear...`);

    // Token expirado, intentar refresh antes de bloquear
    return authService.refreshToken().pipe(
      map(() => {
        console.log(`   ✅ Refresh exitoso - Acceso PERMITIDO`);
        console.log('───────────────────────────────────────────────────────────────');
        return true;
      }),
      catchError(() => {
        console.log(`   ❌ Refresh falló - Acceso DENEGADO`);
        console.log('───────────────────────────────────────────────────────────────');
        router.navigate(['/login'], {
          queryParams: { returnUrl: state.url }
        });
        return of(false);
      })
    );
  }

  console.log(`   → Token no expirado localmente`);
  console.log(`   → Verificando con backend...`);

  // Hay token válido localmente, verificar con backend
  return authService.checkAuthStatus().pipe(
    map(status => {
      if (status.authenticated) {
        console.log(`   ✅ Backend confirmó token válido - Acceso PERMITIDO`);
        console.log('───────────────────────────────────────────────────────────────');
        return true;
      } else {
        // Token inválido en backend
        console.log(`   ❌ Backend rechazó el token - Acceso DENEGADO`);
        console.log('───────────────────────────────────────────────────────────────');
        router.navigate(['/login'], {
          queryParams: { returnUrl: state.url }
        });
        return false;
      }
    }),
    catchError(() => {
      // Error de red, pero hay token local - permitir acceso optimista
      console.log(`   ⚠️ Error de red verificando con backend`);
      console.log(`   → Permitiendo acceso OPTIMISTA (hay token local)`);
      console.log('───────────────────────────────────────────────────────────────');
      return of(true);
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

    return authService.getUserProfile().pipe(
      map(user => {
        // Los roles vienen como "ROLE_USER", "ROLE_ADMIN", etc.
        const hasRole = user.roles.some(role =>
          role === requiredRole || role === `ROLE_${requiredRole}`
        );

        if (hasRole) {
          return true;
        } else {
          console.warn(`RoleGuard: Usuario no tiene rol ${requiredRole}`);
          router.navigate(['/dashboard']); // Volver al dashboard
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

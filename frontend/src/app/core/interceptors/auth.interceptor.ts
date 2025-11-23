import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Interceptor HTTP para el patrón BFF con cookies.
 *
 * Responsabilidades:
 * 1. Añadir withCredentials=true a TODAS las peticiones al backend
 * 2. Manejar errores de autenticación (401, 403)
 * 3. Redirigir al login cuando sea necesario
 *
 * CRÍTICO para BFF:
 * - withCredentials=true es OBLIGATORIO para enviar cookies HttpOnly
 * - Sin esto, las cookies no se envían y todas las peticiones fallan
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  // CRÍTICO: Añadir withCredentials a la petición
  // Esto hace que el navegador envíe las cookies automáticamente
  const authReq = req.clone({
    withCredentials: true
  });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // Manejar errores de autenticación
      if (error.status === 401) {
        // 401 Unauthorized: No hay sesión o token expirado
        console.warn('No autenticado (401), redirigiendo a login');
        router.navigate(['/login']);
      } else if (error.status === 403) {
        // 403 Forbidden: Usuario autenticado pero sin permisos
        console.warn('Acceso denegado (403)');
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

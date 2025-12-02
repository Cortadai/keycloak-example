import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError, Observable, BehaviorSubject, filter, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Prefijo para logs del interceptor
 */
const LOG_PREFIX = '🔒 [Interceptor]';

/**
 * Flag para evitar múltiples refreshes simultáneos
 */
let isRefreshing = false;
const refreshTokenSubject: BehaviorSubject<string | null> = new BehaviorSubject<string | null>(null);

/**
 * Interceptor HTTP para el patrón BFF con Binding (JWT + Cookie).
 *
 * Responsabilidades:
 * 1. Añadir header Authorization: Bearer {token} a todas las peticiones
 * 2. Añadir withCredentials: true para que las cookies viajen automáticamente
 * 3. Manejar errores 401 con refresh automático y retry
 * 4. Redirigir al login si el refresh falla
 *
 * El binding funciona así:
 * - JWT en localStorage → se añade como header Authorization
 * - Cookie HttpOnly con hash del fingerprint → viaja automáticamente con withCredentials
 * - Ambos son necesarios para autenticarse (protección XSS + CSRF)
 *
 * Estrategia de refresh:
 * - Proactivo: Timer en AuthService (antes de expirar)
 * - Reactivo: Este interceptor (en caso de 401)
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authService = inject(AuthService);

  // Extraer la parte final de la URL para logs más legibles
  const urlPath = new URL(req.url, window.location.origin).pathname;

  // SIEMPRE añadir withCredentials para que las cookies viajen (binding)
  req = req.clone({ withCredentials: true });

  // No añadir token a endpoints públicos de auth (exchange)
  if (isAuthEndpoint(req.url)) {
    console.log(`${LOG_PREFIX} ${req.method} ${urlPath} (sin token - endpoint público, con credentials)`);
    return next(req);
  }

  // Obtener token de localStorage
  const token = authService.getStoredToken();

  // Si hay token, añadirlo al header
  if (token) {
    console.log(`${LOG_PREFIX} ${req.method} ${urlPath}`);
    console.log(`   → Header: Authorization: Bearer ${token.substring(0, 15)}...`);
    console.log(`   → Cookie: Fingerprint (automática con credentials)`);
    req = addTokenToRequest(req, token);
  } else {
    console.log(`${LOG_PREFIX} ${req.method} ${urlPath} (sin token en localStorage)`);
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Si es 401 y no es un endpoint de auth, intentar refresh
      if (error.status === 401 && !isAuthEndpoint(req.url)) {
        console.warn(`${LOG_PREFIX} ⚠️ Respuesta 401 en ${urlPath} - Intentando refresh...`);
        return handle401Error(req, next, router, authService);
      }

      // 403 Forbidden: Usuario autenticado pero sin permisos
      if (error.status === 403) {
        console.warn(`${LOG_PREFIX} ❌ Acceso denegado (403) en ${urlPath}`);
      }

      // Error de red o CORS
      if (error.status === 0) {
        console.error(`${LOG_PREFIX} ❌ Error de red/CORS en ${urlPath}:`, error);
      }

      return throwError(() => error);
    })
  );
};

/**
 * Añade el token Bearer al header Authorization.
 * Mantiene withCredentials para que las cookies sigan viajando.
 */
function addTokenToRequest(req: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
  return req.clone({
    withCredentials: true,
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });
}

/**
 * Verifica si la URL es un endpoint de autenticación que no necesita token.
 */
function isAuthEndpoint(url: string): boolean {
  // Solo estos endpoints no necesitan Bearer token
  // /status y /refresh SÍ necesitan token
  const authEndpoints = [
    '/api/auth/exchange',
    '/api/auth/login'
  ];
  return authEndpoints.some(endpoint => url.includes(endpoint));
}

/**
 * Maneja errores 401 con refresh automático y retry de la petición.
 */
function handle401Error(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  router: Router,
  authService: AuthService
): Observable<any> {

  const urlPath = new URL(req.url, window.location.origin).pathname;

  if (!isRefreshing) {
    isRefreshing = true;
    refreshTokenSubject.next(null);

    console.log(`${LOG_PREFIX} 🔃 Iniciando refresh reactivo por 401 en ${urlPath}`);

    return authService.refreshToken().pipe(
      switchMap(response => {
        isRefreshing = false;
        refreshTokenSubject.next(response.accessToken);

        console.log(`${LOG_PREFIX} ✅ Refresh exitoso, reintentando petición original: ${urlPath}`);
        // Reintentar la petición original con el nuevo token
        return next(addTokenToRequest(req, response.accessToken));
      }),
      catchError(err => {
        isRefreshing = false;
        refreshTokenSubject.next(null);

        // Refresh falló, redirigir a login
        console.warn(`${LOG_PREFIX} ❌ Refresh falló, redirigiendo a /login`);
        router.navigate(['/login']);

        return throwError(() => err);
      })
    );
  }

  // Ya hay un refresh en progreso, esperar a que termine
  console.log(`${LOG_PREFIX} ⏳ Refresh ya en progreso, esperando para ${urlPath}...`);
  return refreshTokenSubject.pipe(
    filter(token => token !== null),
    take(1),
    switchMap(token => {
      console.log(`${LOG_PREFIX} ✅ Refresh completado, reintentando: ${urlPath}`);
      return next(addTokenToRequest(req, token!));
    })
  );
}

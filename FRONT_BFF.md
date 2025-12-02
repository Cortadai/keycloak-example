# Frontend - Angular BFF con Binding (JWT + Cookie HttpOnly)

Documentacion completa del frontend implementado con Angular 21 (Standalone Components + Signals) para el patron "Llave Partida".

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Patron Llave Partida en Frontend](#patron-llave-partida-en-frontend)
- [Componentes Principales](#componentes-principales)
- [Servicios](#servicios)
- [Guards y Seguridad](#guards-y-seguridad)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura

### Patron BFF con Binding en el Frontend

El frontend implementa el lado cliente del patron BFF con Binding donde:

1. **JWT en localStorage** - Contiene claim `fingerprint`
2. **Cookie HttpOnly** - Contiene hash SHA-256 del fingerprint (gestionada por navegador)
3. **Authorization Bearer header** - En cada peticion
4. **withCredentials: true** - Para que cookie viaje automaticamente
5. **Guards funcionales** - Protegen rutas sensibles
6. **Signals** - Para state management reactivo

### Diagrama de Flujo

```
+---------------------------------------------+
|         Angular Application                 |
|                                             |
|  +---------------------------------------+  |
|  |         AppConfig                     |  |
|  |  - Router                             |  |
|  |  - HttpClient                         |  |
|  |  - authInterceptor                    |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      authInterceptor                  |  |
|  |  - Anade Bearer token a headers       |  |
|  |  - withCredentials: true (BINDING!)   |  |
|  |  - Manejo 401: refresh + retry        |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      AuthService                      |  |
|  |  - login() / logout()                 |  |
|  |  - exchangeCode()                     |  |
|  |  - refreshToken()                     |  |
|  |  - localStorage management            |  |
|  |  (Cookie la gestiona el navegador)    |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      Components                       |  |
|  |  - LoginComponent (info binding)      |  |
|  |  - CallbackComponent                  |  |
|  |  - DashboardComponent (info binding)  |  |
|  +---------------------------------------+  |
+---------------------------------------------+
```

---

## Patron Llave Partida en Frontend

### Concepto

```
+-----------------------------------------------------------+
|                                                           |
|   JWT en localStorage      Cookie HttpOnly                |
|   (gestionado por app)     (gestionado por navegador)     |
|                                                           |
|   +------------------+     +------------------+           |
|   | accessToken:     |     | Fingerprint:     |           |
|   | eyJhbGci...      |     | a1b2c3d4e5...    |           |
|   | (con fingerprint)|     | (hash SHA-256)   |           |
|   +------------------+     +------------------+           |
|           |                        |                      |
|           v                        v                      |
|   Interceptor anade        Viaja automatico              |
|   Authorization: Bearer    con withCredentials           |
|                                                           |
|   ========================================================|
|                                                           |
|   AMBOS son necesarios para autenticarse!                 |
|                                                           |
+-----------------------------------------------------------+
```

### Responsabilidades del Frontend

| Elemento | Responsable | Accion |
|----------|-------------|--------|
| JWT | App (AuthService) | Guardar en localStorage, enviar en header |
| Cookie | Navegador | Se guarda y envia automaticamente |
| Binding | Backend | Valida que ambos coincidan |

---

## Componentes Principales

### 1. LoginComponent

**Ubicacion**: `frontend/src/app/features/login/login.component.ts`

**Responsabilidad**: Pantalla de login con informacion del patron Binding.

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <h1>Keycloak Spring Demo</h1>
        <p class="subtitle">Patron BFF con Binding (JWT + Cookie HttpOnly)</p>

        <button class="login-button" (click)="login()">
          Login con Keycloak
        </button>

        <div class="security-info">
          <p>Seguridad "Llave Partida": JWT en localStorage +
             Cookie HttpOnly con fingerprint.</p>
          <p>Ambos son necesarios para autenticarse.</p>
          <p>El Refresh Token permanece seguro en Redis.</p>
        </div>
      </div>
    </div>
  `
})
export class LoginComponent {
  private authService = inject(AuthService);

  login(): void {
    this.authService.login();
  }
}
```

---

### 2. CallbackComponent

**Ubicacion**: `frontend/src/app/features/callback/callback.component.ts`

**Responsabilidad**: Intercambiar codigo temporal por JWT + Cookie.

```typescript
@Component({
  selector: 'app-callback',
  standalone: true,
  template: `
    @if (error()) {
      <div class="error-card">
        <h2>Error de Autenticacion</h2>
        <p>{{ error() }}</p>
      </div>
    } @else {
      <div class="loading">
        <div class="spinner"></div>
        <p>Completando autenticacion...</p>
      </div>
    }
  `
})
export class CallbackComponent implements OnInit {
  error = signal<string | null>(null);

  ngOnInit(): void {
    const code = this.route.snapshot.queryParamMap.get('code');

    if (!code) {
      this.error.set('No se recibio codigo');
      return;
    }

    // Intercambiar codigo por JWT
    // Backend tambien setea Cookie HttpOnly en la respuesta
    this.authService.exchangeCode(code).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => this.error.set(err.message)
    });
  }
}
```

**Nota importante**: El backend devuelve:
- **Body**: `{ accessToken, expiresIn }` → guardamos en localStorage
- **Header**: `Set-Cookie: Fingerprint=xxx; HttpOnly` → navegador guarda automaticamente

---

### 3. DashboardComponent

**Ubicacion**: `frontend/src/app/features/dashboard/dashboard.component.ts`

**Responsabilidad**: Dashboard con informacion de seguridad del Binding.

```typescript
@Component({
  selector: 'app-dashboard',
  template: `
    <div class="dashboard-container">
      @if (user()) {
        <div class="welcome-card">
          <h1>Bienvenido, {{ user()!.name }}!</h1>
        </div>

        <div class="security-info">
          <h3>Seguridad BFF con Binding (Llave Partida)</h3>
          <ul>
            <li><strong>JWT con fingerprint</strong> en localStorage</li>
            <li><strong>Cookie HttpOnly</strong> con hash SHA-256</li>
            <li><strong>Binding</strong>: Ambos necesarios para autenticarse</li>
            <li><strong>Proteccion XSS</strong>: Atacante roba JWT pero NO cookie</li>
            <li><strong>Proteccion CSRF</strong>: Atacante tiene cookie pero NO JWT</li>
            <li>Refresh Token seguro en Redis</li>
            <li>Rotacion de fingerprint en cada refresh</li>
          </ul>
        </div>
      }
    </div>
  `
})
export class DashboardComponent {
  user = signal<User | null>(null);
  // ...
}
```

---

## Servicios

### AuthService

**Ubicacion**: `frontend/src/app/core/services/auth.service.ts`

**Responsabilidades**:
- Gestionar JWT en localStorage
- Comunicacion con backend BFF
- Refresh proactivo y reactivo
- **NO gestiona Cookie** (lo hace el navegador)

```typescript
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API_URL = 'http://localhost:8081/api';
  private readonly TOKEN_KEY = 'access_token';
  private readonly EXPIRY_KEY = 'token_expiry';

  /**
   * Inicia login OAuth2.
   * Redirige al backend -> Keycloak.
   */
  login(): void {
    window.location.href = `${this.API_URL}/auth/login`;
  }

  /**
   * Intercambia codigo temporal por JWT.
   *
   * IMPORTANTE: El backend tambien setea Cookie HttpOnly
   * en el header Set-Cookie de la respuesta.
   * El navegador la guarda automaticamente.
   */
  exchangeCode(code: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(
      `${this.API_URL}/auth/exchange`,
      { code }
    ).pipe(
      tap(response => {
        // Solo guardamos JWT en localStorage
        // Cookie la gestiona el navegador automaticamente
        this.setToken(response.accessToken, response.expiresIn);
        this.scheduleTokenRefresh(response.expiresIn);
      })
    );
  }

  /**
   * Refresca el JWT.
   *
   * Backend:
   * 1. Valida JWT actual (puede estar expirado)
   * 2. Usa refresh token de Redis
   * 3. Genera NUEVO fingerprint (rotacion)
   * 4. Devuelve nuevo JWT + actualiza Cookie
   */
  refreshToken(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>(
      `${this.API_URL}/auth/refresh`,
      {}
    ).pipe(
      tap(response => {
        this.setToken(response.accessToken, response.expiresIn);
        this.scheduleTokenRefresh(response.expiresIn);
      })
    );
  }

  /**
   * Cierra sesion.
   *
   * Backend:
   * 1. Revoca tokens en Keycloak
   * 2. Elimina refresh de Redis
   * 3. Elimina Cookie (Max-Age=0)
   */
  logout(): Observable<LogoutResponse> {
    return this.http.post<LogoutResponse>(
      `${this.API_URL}/auth/logout`,
      {}
    ).pipe(
      tap(() => this.clearTokens())
    );
  }

  private setToken(token: string, expiresIn: number): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    const expiryTime = Date.now() + (expiresIn * 1000);
    localStorage.setItem(this.EXPIRY_KEY, expiryTime.toString());
  }

  private clearTokens(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.EXPIRY_KEY);
    // Cookie la elimina el backend con Max-Age=0
  }
}
```

---

### authInterceptor

**Ubicacion**: `frontend/src/app/core/interceptors/auth.interceptor.ts`

**Responsabilidad CRITICA**:
- Anadir Bearer token
- **withCredentials: true** para que cookie viaje

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // CRITICO: withCredentials para que cookie viaje automaticamente
  // Sin esto, el binding NO funciona
  req = req.clone({ withCredentials: true });

  // Endpoints publicos no necesitan Bearer
  if (isAuthEndpoint(req.url)) {
    return next(req);
  }

  // Anadir Bearer token si existe
  const token = authService.getStoredToken();
  if (token) {
    req = req.clone({
      withCredentials: true,  // Mantener!
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Intentar refresh y reintentar
        return authService.refreshToken().pipe(
          switchMap(() => {
            const newToken = authService.getStoredToken();
            const retryReq = req.clone({
              withCredentials: true,
              setHeaders: {
                Authorization: `Bearer ${newToken}`
              }
            });
            return next(retryReq);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
```

**Por que withCredentials es CRITICO?**

```
Sin withCredentials:
  Request: Authorization: Bearer xxx
  Cookie: (no viaja)
  Backend: "Cookie faltante" -> 401

Con withCredentials:
  Request: Authorization: Bearer xxx
  Cookie: Fingerprint=abc123... (viaja automatico)
  Backend: Valida binding -> OK
```

---

## Guards y Seguridad

### authGuard

**Ubicacion**: `frontend/src/app/core/guards/auth.guard.ts`

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // 1. Hay token en localStorage?
  const token = authService.getStoredToken();
  if (!token) {
    router.navigate(['/login']);
    return of(false);
  }

  // 2. Token expirado localmente?
  if (authService.isTokenExpired()) {
    return authService.refreshToken().pipe(
      map(() => true),
      catchError(() => {
        router.navigate(['/login']);
        return of(false);
      })
    );
  }

  // 3. Verificar con backend (incluye validacion de binding)
  return authService.checkAuthStatus().pipe(
    map(status => {
      if (status.authenticated) return true;
      router.navigate(['/login']);
      return false;
    })
  );
};
```

---

## Flujo Completo del Binding

### Login

```
1. Usuario click "Login con Keycloak"
2. window.location.href = /api/auth/login
3. Backend redirige a Keycloak
4. Usuario se autentica
5. Keycloak callback a backend
6. Backend genera fingerprint + hash
7. Backend crea JWT con claim fingerprint
8. Backend redirige a /callback?code=xxx
9. Frontend llama POST /api/auth/exchange
10. Response:
    - Body: { accessToken: "eyJ...", expiresIn: 900 }
    - Header: Set-Cookie: Fingerprint=abc123; HttpOnly; SameSite=Strict
11. Frontend guarda JWT en localStorage
12. Navegador guarda Cookie automaticamente
13. Redirect a /dashboard
```

### Peticion Autenticada

```
1. Frontend hace peticion a /api/user/me
2. Interceptor clona request:
   - withCredentials: true
   - Authorization: Bearer {jwt}
3. Navegador automaticamente anade Cookie
4. Request final:
   - Header: Authorization: Bearer eyJ...
   - Cookie: Fingerprint=abc123...
5. Backend (FingerprintValidationFilter):
   - Extrae fingerprint del JWT
   - Extrae hash de Cookie
   - Valida: SHA-256(fingerprint) == hash
6. Si valido: continua a Spring Security
7. Si invalido: 401 "Binding invalido"
```

### Refresh

```
1. Token proximo a expirar (o 401)
2. POST /api/auth/refresh con Bearer actual
3. Backend:
   - Valida JWT (permite expirado)
   - Genera NUEVO fingerprint (rotacion)
   - Crea nuevo JWT con nuevo fingerprint
   - Actualiza Cookie con nuevo hash
4. Response:
   - Body: { accessToken: "eyJ...(nuevo)", expiresIn: 900 }
   - Header: Set-Cookie: Fingerprint=xyz789; HttpOnly (nuevo hash)
5. Frontend actualiza localStorage
6. Navegador actualiza Cookie
```

---

## Testing

### Test 1: Verificar Binding en DevTools

1. Login en la aplicacion
2. Abrir DevTools (F12)
3. **Application** -> **Local Storage**:
   - Ver `access_token` y `token_expiry`
4. **Application** -> **Cookies**:
   - Ver `Fingerprint` con flag HttpOnly
5. **Network** -> filtrar `/api/user/me`:
   - Request Headers: `Authorization: Bearer xxx`
   - Request Headers: `Cookie: Fingerprint=xxx`

### Test 2: Verificar withCredentials

En **Network** tab:
1. Click en una peticion a `/api/*`
2. En Request Headers verificar que aparece `Cookie:`
3. Si no aparece, revisar que interceptor tenga `withCredentials: true`

### Test 3: Simular Falta de Cookie

1. En DevTools -> Application -> Cookies
2. Eliminar cookie `Fingerprint`
3. Hacer una peticion (recargar dashboard)
4. Verificar error 401: "Binding requerido - cookie faltante"

### Test 4: Verificar Rotacion de Fingerprint

1. Login normal
2. Copiar valor de cookie `Fingerprint`
3. Esperar a que token expire o forzar refresh
4. Verificar que cookie `Fingerprint` tiene valor DIFERENTE

---

## Troubleshooting

### Error: "Binding requerido - cookie faltante"

**Causa**: Cookie no viaja en la peticion.

**Solucion**:
1. Verificar `withCredentials: true` en interceptor
2. Verificar CORS en backend tiene `allowCredentials: true`
3. Verificar origen exacto (no wildcard `*`)

### Error: "Binding invalido"

**Causa**: Hash no coincide.

**Posibles razones**:
- Usaste JWT viejo despues de refresh
- Manipulacion del JWT
- Intento de usar token robado

**Solucion**: Hacer logout y login nuevamente

### Cookie no aparece en DevTools Cookies

**Esto es CORRECTO** - La cookie es HttpOnly pero aun deberia aparecer en la lista de cookies (solo que JS no puede acceder). Si no aparece:
1. Verificar que backend esta seteando `Set-Cookie`
2. Verificar Network tab -> Response Headers -> `Set-Cookie`
3. Verificar que dominio/path coincide

### Peticiones sin Cookie en Network

**Causa**: `withCredentials` no esta configurado.

**Verificar en interceptor**:
```typescript
req = req.clone({ withCredentials: true });
```

---

## Modelo de Datos

### user.model.ts

```typescript
export interface User {
  username: string;
  email: string;
  name: string;
  roles: string[];
  authenticated: boolean;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
}

export interface AuthStatus {
  authenticated: boolean;
  username?: string;
}

export interface LogoutResponse {
  success: boolean;
  message: string;
}

export interface ExchangeRequest {
  code: string;
}
```

---

## Diferencias con Rama "Headers"

| Aspecto | Headers (rama anterior) | Binding (esta rama) |
|---------|-------------------------|---------------------|
| Cookie | No usa | HttpOnly con hash |
| withCredentials | false | **true** |
| Proteccion XSS | Vulnerable | Protegido |
| Proteccion CSRF | Protegido | Protegido |
| Complejidad frontend | Menor | Mayor (pero minima) |
| Rotacion fingerprint | No aplica | En cada refresh |

---

**Ir a**: [README](README.md) | [Backend](BACK_BFF.md) | [Mejoras](MEJORAS.md)

# Frontend - Angular BFF con Headers + Redis

Documentación completa del frontend implementado con Angular 21 (Standalone Components + Signals).

---

## Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Componentes Principales](#componentes-principales)
- [Servicios y State Management](#servicios-y-state-management)
- [Guards y Seguridad](#guards-y-seguridad)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura

### Patron BFF con Headers en el Frontend

El frontend implementa el lado cliente del patron BFF donde:

1. **Access Token en localStorage** - Accesible para enviar en headers
2. **Authorization Bearer header** en cada peticion
3. **Refresh Token en Redis** - NUNCA llega al frontend
4. **Guards funcionales** protegen rutas sensibles
5. **Signals** para state management reactivo
6. **Standalone components** sin NgModules

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
|  |  - Manejo 401: refresh + retry        |  |
|  |  - Manejo 403: acceso denegado        |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      AuthService                      |  |
|  |  - login() / logout()                 |  |
|  |  - exchangeCode()                     |  |
|  |  - refreshToken()                     |  |
|  |  - checkAuthStatus()                  |  |
|  |  - scheduleTokenRefresh()             |  |
|  |  - localStorage management            |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      authGuard                        |  |
|  |  - Verifica token local               |  |
|  |  - Verifica con backend               |  |
|  |  - Intenta refresh si expiro          |  |
|  |  - Redirect a /login si no auth       |  |
|  +---------------------------------------+  |
|                                             |
|  +---------------------------------------+  |
|  |      Components                       |  |
|  |  - LoginComponent                     |  |
|  |  - CallbackComponent                  |  |
|  |  - DashboardComponent                 |  |
|  +---------------------------------------+  |
+---------------------------------------------+
```

### Flujo de Autenticacion

```
Usuario          Frontend           Backend            Redis           Keycloak
   |                 |                 |                 |                 |
   | Click Login     |                 |                 |                 |
   |---------------->|                 |                 |                 |
   |                 | GET /api/auth/login               |                 |
   |                 |---------------->|                 |                 |
   |                 |                 | OAuth2 Redirect |                 |
   |                 |                 |---------------------------------->|
   |                 |                 |                 |                 |
   | Login Form      |                 |                 |                 |
   |<-------------------------------------------------------------------- |
   |                 |                 |                 |                 |
   | Credenciales    |                 |                 |                 |
   |-------------------------------------------------------------------->|
   |                 |                 |                 |                 |
   |                 |                 | Callback + Auth Code              |
   |                 |                 |<----------------------------------|
   |                 |                 |                 |                 |
   |                 |                 | Store temp_code |                 |
   |                 |                 | (UUID, TTL 30s) |                 |
   |                 |                 |---------------->|                 |
   |                 |                 |                 |                 |
   |                 | Redirect /callback?code=uuid      |                 |
   |                 |<----------------|                 |                 |
   |                 |                 |                 |                 |
   |                 | POST /api/auth/exchange           |                 |
   |                 | { code: uuid }  |                 |                 |
   |                 |---------------->|                 |                 |
   |                 |                 | Get temp_code   |                 |
   |                 |                 |---------------->|                 |
   |                 |                 |<----------------|                 |
   |                 |                 | Store refresh   |                 |
   |                 |                 |---------------->|                 |
   |                 |                 |                 |                 |
   |                 | { accessToken, expiresIn }        |                 |
   |                 |<----------------|                 |                 |
   |                 |                 |                 |                 |
   |                 | localStorage.set()                |                 |
   |                 | Schedule refresh                  |                 |
   |                 |                 |                 |                 |
   | Dashboard       |                 |                 |                 |
   |<----------------|                 |                 |                 |
```

---

## Componentes Principales

### 1. app.config.ts

**Ubicacion**: `frontend/src/app/app.config.ts`

**Responsabilidad**: Configuracion global de Angular.

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor])  // Interceptor global
    )
  ]
};
```

**Caracteristicas**:
- Router configurado con rutas
- HttpClient con interceptor `authInterceptor`
- No necesita NgModules (standalone)

---

### 2. app.routes.ts

**Ubicacion**: `frontend/src/app/app.routes.ts`

**Responsabilidad**: Definicion de rutas con proteccion.

```typescript
export const routes: Routes = [
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'callback',
    component: CallbackComponent  // Nueva ruta para intercambio
  },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]  // Protegida con guard
  },
  {
    path: '**',
    redirectTo: '/login'
  }
];
```

**Rutas**:
- `/login`: Pantalla de login
- `/callback`: Intercambio de codigo temporal por accessToken
- `/dashboard`: Requiere autenticacion (authGuard)
- Cualquier ruta no definida -> redirect a `/login`

---

### 3. LoginComponent

**Ubicacion**: `frontend/src/app/features/login/login.component.ts`

**Responsabilidad**: Pantalla de login con diseno moderno.

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="lock-icon">...</div>
        <h1>Keycloak Spring Demo</h1>
        <p class="subtitle">Patron BFF con JWT en Headers + Redis</p>

        <button class="login-button" (click)="login()">
          Login con Keycloak
        </button>

        <div class="security-info">
          <p>El Access Token se almacena en localStorage.</p>
          <p>El Refresh Token permanece seguro en Redis
             (nunca llega al navegador).</p>
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

**Caracteristicas**:
- Standalone component
- Inline template y styles
- Diseno moderno con gradiente
- Un solo boton: "Login con Keycloak"

---

### 4. CallbackComponent

**Ubicacion**: `frontend/src/app/features/callback/callback.component.ts`

**Responsabilidad**: Intercambiar codigo temporal por accessToken.

```typescript
@Component({
  selector: 'app-callback',
  standalone: true,
  template: `
    <div class="callback-container">
      @if (error()) {
        <div class="error-card">
          <h2>Error de Autenticacion</h2>
          <p>{{ error() }}</p>
          <button (click)="goToLogin()">Volver al Login</button>
        </div>
      } @else {
        <div class="loading">
          <div class="spinner"></div>
          <p>Completando autenticacion...</p>
        </div>
      }
    </div>
  `
})
export class CallbackComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  error = signal<string | null>(null);

  ngOnInit(): void {
    // Obtener codigo temporal de query params
    const code = this.route.snapshot.queryParamMap.get('code');

    if (!code) {
      this.error.set('No se recibio codigo de autenticacion');
      return;
    }

    // Intercambiar codigo por accessToken
    this.authService.exchangeCode(code).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Error al completar autenticacion');
      }
    });
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}
```

**Caracteristicas**:
- Extrae codigo temporal de `?code=xxx`
- Llama a `/api/auth/exchange` para obtener accessToken
- Guarda token en localStorage
- Redirige a dashboard o muestra error

---

### 5. DashboardComponent

**Ubicacion**: `frontend/src/app/features/dashboard/dashboard.component.ts`

**Responsabilidad**: Dashboard del usuario autenticado.

```typescript
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <nav class="navbar">
        <h1>Dashboard</h1>
        <button class="logout-button" (click)="logout()">
          Logout
        </button>
      </nav>

      @if (loading()) {
        <div class="loading">Cargando...</div>
      }

      @else if (error()) {
        <div class="error">Error: {{ error() }}</div>
      }

      @else if (user()) {
        <div class="content">
          <div class="welcome-card">
            <div class="avatar">{{ getInitials(user()!.name) }}</div>
            <h2>Bienvenido, {{ user()!.name }}!</h2>
          </div>

          <div class="info-grid">
            <div class="info-card">
              <h3>Usuario</h3>
              <p>{{ user()!.username }}</p>
            </div>
            <div class="info-card">
              <h3>Email</h3>
              <p>{{ user()!.email }}</p>
            </div>
            <div class="info-card">
              <h3>Roles</h3>
              <div class="roles">
                @for (role of user()!.roles; track role) {
                  <span class="role-badge">{{ formatRole(role) }}</span>
                }
              </div>
            </div>
          </div>

          <div class="security-section">
            <h3>Informacion de Seguridad BFF</h3>
            <ul>
              <li>Access Token en localStorage + header Authorization Bearer</li>
              <li>Refresh Token seguro en Redis (nunca expuesto al frontend)</li>
              <li>Refresh proactivo antes de expirar + reactivo en 401</li>
              <li>Sesion unica por usuario (nuevo login invalida el anterior)</li>
              <li>Autenticacion OAuth2 gestionada por Keycloak</li>
            </ul>
          </div>
        </div>
      }
    </div>
  `
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  user = signal<User | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadUserProfile();
  }

  loadUserProfile(): void {
    this.authService.getUserProfile().subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Error al cargar perfil');
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }
}
```

**Caracteristicas**:
- Signals para state management
- Nueva sintaxis `@if` / `@else` / `@for` de Angular
- Loading, error y success states
- Responsive design

---

## Servicios y State Management

### AuthService

**Ubicacion**: `frontend/src/app/core/services/auth.service.ts`

**Responsabilidades**:
- Gestionar autenticacion
- localStorage para accessToken
- Comunicacion con backend BFF
- Refresh proactivo y reactivo

```typescript
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private readonly API_URL = 'http://localhost:8081/api';

  private readonly TOKEN_KEY = 'access_token';
  private readonly EXPIRY_KEY = 'token_expiry';
  private refreshTimer: any = null;

  /**
   * Inicia el flujo OAuth2 con Keycloak.
   * Redirige a backend que redirige a Keycloak.
   */
  login(): void {
    console.log('[AuthService] Iniciando login...');
    window.location.href = `${this.API_URL}/auth/login`;
  }

  /**
   * Intercambia codigo temporal por accessToken.
   * El backend devuelve accessToken + expiresIn.
   * El refreshToken se guarda en Redis (nunca llega aqui).
   */
  exchangeCode(code: string): Observable<any> {
    console.log('[AuthService] Intercambiando codigo temporal...');

    return this.http.post<any>(`${this.API_URL}/auth/exchange`, { code }).pipe(
      tap(response => {
        // Guardar accessToken en localStorage
        this.setToken(response.accessToken, response.expiresIn);

        // Programar refresh proactivo
        this.scheduleTokenRefresh(response.expiresIn);

        console.log('[AuthService] Token guardado, refresh programado');
      })
    );
  }

  /**
   * Guarda token y timestamp de expiracion en localStorage.
   */
  private setToken(token: string, expiresIn: number): void {
    localStorage.setItem(this.TOKEN_KEY, token);

    // Calcular timestamp de expiracion
    const expiryTime = Date.now() + (expiresIn * 1000);
    localStorage.setItem(this.EXPIRY_KEY, expiryTime.toString());
  }

  /**
   * Obtiene el token almacenado.
   */
  getStoredToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  /**
   * Verifica si el token ha expirado localmente.
   */
  isTokenExpired(): boolean {
    const expiryStr = localStorage.getItem(this.EXPIRY_KEY);
    if (!expiryStr) return true;

    const expiry = parseInt(expiryStr, 10);
    // Considerar expirado 30 segundos antes (margen de seguridad)
    return Date.now() > (expiry - 30000);
  }

  /**
   * Programa el refresh proactivo del token.
   * Se ejecuta 2 minutos antes de que expire.
   */
  scheduleTokenRefresh(expiresIn: number): void {
    // Cancelar timer anterior si existe
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    // Refrescar 2 minutos antes de expirar
    const refreshTime = (expiresIn - 120) * 1000;

    if (refreshTime > 0) {
      console.log(`[AuthService] Refresh programado en ${Math.round(refreshTime / 60000)} minutos`);

      this.refreshTimer = setTimeout(() => {
        console.log('[AuthService] Ejecutando refresh proactivo...');
        this.refreshToken().subscribe({
          next: () => console.log('[AuthService] Refresh proactivo exitoso'),
          error: () => console.warn('[AuthService] Refresh proactivo fallo')
        });
      }, refreshTime);
    }
  }

  /**
   * Solicita un nuevo accessToken usando el refreshToken en Redis.
   * Requiere enviar el Bearer token actual para identificar al usuario.
   */
  refreshToken(): Observable<any> {
    console.log('[AuthService] Solicitando refresh token...');

    return this.http.post<any>(`${this.API_URL}/auth/refresh`, {}).pipe(
      tap(response => {
        this.setToken(response.accessToken, response.expiresIn);
        this.scheduleTokenRefresh(response.expiresIn);
        console.log('[AuthService] Token refrescado exitosamente');
      })
    );
  }

  /**
   * Verifica si hay sesion activa.
   * Usado por authGuard antes de cada navegacion.
   */
  checkAuthStatus(): Observable<any> {
    return this.http.get<any>(`${this.API_URL}/auth/status`).pipe(
      catchError(() => of({ authenticated: false }))
    );
  }

  /**
   * Obtiene perfil completo del usuario.
   */
  getUserProfile(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/user/me`);
  }

  /**
   * Cierra sesion.
   * Elimina token local y revoca refresh en Redis.
   */
  logout(): Observable<any> {
    console.log('[AuthService] Cerrando sesion...');

    return this.http.post(`${this.API_URL}/auth/logout`, {}).pipe(
      tap(() => {
        this.clearTokens();
        console.log('[AuthService] Sesion cerrada');
      }),
      catchError(() => {
        this.clearTokens();
        return of(null);
      })
    );
  }

  /**
   * Limpia tokens del localStorage.
   */
  clearTokens(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.EXPIRY_KEY);

    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }
}
```

**Por que localStorage?**
- El accessToken necesita enviarse en headers
- JavaScript necesita acceso para anadir `Authorization: Bearer`
- El refreshToken NUNCA esta en localStorage (solo en Redis)

**Refresh proactivo vs reactivo:**
- **Proactivo**: Timer que refresca 2 min antes de expirar
- **Reactivo**: Interceptor que reintenta en 401

---

## Guards y Seguridad

### authGuard

**Ubicacion**: `frontend/src/app/core/guards/auth.guard.ts`

**Responsabilidad**: Proteger rutas que requieren autenticacion.

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  console.log('[AuthGuard] Verificando acceso a:', state.url);

  // 1. Verificacion rapida: hay token en localStorage?
  const token = authService.getStoredToken();

  if (!token) {
    console.log('[AuthGuard] No hay token, redirigiendo a login');
    router.navigate(['/login'], {
      queryParams: { returnUrl: state.url }
    });
    return of(false);
  }

  // 2. Verificacion local: token expirado?
  if (authService.isTokenExpired()) {
    console.log('[AuthGuard] Token expirado, intentando refresh...');

    return authService.refreshToken().pipe(
      map(() => {
        console.log('[AuthGuard] Refresh exitoso, acceso permitido');
        return true;
      }),
      catchError(() => {
        console.log('[AuthGuard] Refresh fallo, redirigiendo a login');
        router.navigate(['/login']);
        return of(false);
      })
    );
  }

  // 3. Verificacion con backend: token valido?
  console.log('[AuthGuard] Verificando con backend...');

  return authService.checkAuthStatus().pipe(
    map(status => {
      if (status.authenticated) {
        console.log('[AuthGuard] Backend confirmo, acceso permitido');
        return true;
      } else {
        console.log('[AuthGuard] Backend rechazo, redirigiendo a login');
        router.navigate(['/login']);
        return false;
      }
    }),
    catchError(() => {
      // Error de red pero hay token local - acceso optimista
      console.log('[AuthGuard] Error de red, permitiendo acceso optimista');
      return of(true);
    })
  );
};
```

**Flujo de verificacion**:
1. Hay token en localStorage?
2. Esta expirado localmente? -> Intentar refresh
3. Verificar con backend si token sigue valido
4. Si error de red -> acceso optimista (hay token local)

---

### roleGuard (Factory)

**Ubicacion**: `frontend/src/app/core/guards/auth.guard.ts`

**Responsabilidad**: Verificar roles especificos.

```typescript
export function roleGuard(requiredRole: string): CanActivateFn {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.getUserProfile().pipe(
      map(user => {
        const hasRole = user.roles.some(role =>
          role === requiredRole || role === `ROLE_${requiredRole}`
        );

        if (hasRole) {
          return true;
        } else {
          console.warn(`[RoleGuard] Usuario no tiene rol ${requiredRole}`);
          router.navigate(['/dashboard']);
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
```

**Uso en rutas**:
```typescript
{
  path: 'admin',
  component: AdminComponent,
  canActivate: [authGuard, roleGuard('ROLE_ADMIN')]
}
```

---

### authInterceptor

**Ubicacion**: `frontend/src/app/core/interceptors/auth.interceptor.ts`

**Responsabilidad**: Anadir Bearer token y manejar 401.

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Endpoints que NO deben llevar token
  if (isAuthEndpoint(req.url)) {
    return next(req);
  }

  // Obtener token de localStorage
  const token = authService.getStoredToken();

  // Clonar request con header Authorization
  let authReq = req;
  if (token) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    console.log(`[Interceptor] Bearer token anadido a ${req.url}`);
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/auth/refresh')) {
        console.log('[Interceptor] 401 recibido, intentando refresh...');

        // Intentar refresh y reintentar peticion original
        return authService.refreshToken().pipe(
          switchMap(() => {
            const newToken = authService.getStoredToken();
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${newToken}`
              }
            });
            console.log('[Interceptor] Reintentando peticion con nuevo token');
            return next(retryReq);
          }),
          catchError((refreshError) => {
            console.log('[Interceptor] Refresh fallo, redirigiendo a login');
            authService.clearTokens();
            router.navigate(['/login']);
            return throwError(() => error);
          })
        );
      }

      if (error.status === 403) {
        console.warn('[Interceptor] Acceso denegado (403)');
      }

      return throwError(() => error);
    })
  );
};

function isAuthEndpoint(url: string): boolean {
  const authEndpoints = [
    '/api/auth/exchange',
    '/api/auth/login'
  ];
  return authEndpoints.some(endpoint => url.includes(endpoint));
}
```

**Caracteristicas**:
- Anade `Authorization: Bearer {token}` a todas las peticiones
- Excepto endpoints de autenticacion inicial
- En 401: intenta refresh automatico y reintenta peticion
- Si refresh falla: limpia tokens y redirige a login

---

## Models

**Ubicacion**: `frontend/src/app/core/models/user.model.ts`

```typescript
export interface User {
  username: string;
  email: string;
  name: string;
  roles: string[];
  authenticated: boolean;
  message?: string;
}

export interface AuthStatus {
  authenticated: boolean;
  username: string | null;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  message?: string;
}

export interface LogoutResponse {
  message: string;
}
```

---

## Testing

### Test 1: Verificar Pantalla de Login

```bash
cd frontend
npm start
```

Abrir `http://localhost:4200`:
- Debe mostrar pantalla de login
- Boton "Login con Keycloak"
- Info sobre JWT en Headers + Redis

---

### Test 2: Flujo de Login

1. Click en "Login con Keycloak"
2. Verificar redirect a Keycloak (`localhost:9090`)
3. Autenticarse
4. Verificar redirect a `localhost:4200/callback?code=xxx`
5. Verificar redirect automatico a `/dashboard`
6. Verificar en DevTools:
   - Application -> Local Storage -> `http://localhost:4200`
   - Buscar `access_token` y `token_expiry`

---

### Test 3: Dashboard Carga Datos

En el dashboard, verificar:
- Navbar con "Dashboard" y boton "Logout"
- Welcome card con avatar (iniciales)
- Info cards: Usuario, Email, Roles
- Roles mostrados como badges
- Seccion de seguridad BFF

---

### Test 4: Verificar Bearer Token en Network Tab

1. Abrir DevTools -> Network
2. Filtrar por `user/me`
3. Click en la peticion
4. Headers -> Request Headers
5. Verificar:
```
Authorization: Bearer eyJhbGc...
```

---

### Test 5: AuthGuard Protege Rutas

1. Hacer logout
2. Limpiar localStorage (DevTools -> Application -> Clear)
3. Intentar navegar a `http://localhost:4200/dashboard`
4. Verificar redirect automatico a `/login`
5. Console debe mostrar logs del guard

---

### Test 6: Logout

1. Estando en dashboard, click en "Logout"
2. Verificar redirect a `/login`
3. Verificar en DevTools que localStorage esta vacio
4. Verificar en Redis que no hay refresh_token del usuario
5. Intentar acceder a `/dashboard` -> debe redirigir a `/login`

---

### Test 7: Refresh Automatico

1. Abrir Console en DevTools
2. Esperar unos minutos
3. Verificar logs de refresh proactivo:
```
[AuthService] Refresh programado en X minutos
[AuthService] Ejecutando refresh proactivo...
[AuthService] Refresh proactivo exitoso
```

---

### Test 8: Refresh Reactivo (401)

1. Modificar `token_expiry` en localStorage a un valor pasado
2. Hacer una peticion (recargar dashboard)
3. Verificar en Console:
```
[Interceptor] 401 recibido, intentando refresh...
[Interceptor] Reintentando peticion con nuevo token
```

---

## Troubleshooting

### Problema: Error CORS

**Sintoma**:
```
Access to fetch at 'http://localhost:8081/api/...' from origin
'http://localhost:4200' has been blocked by CORS policy
```

**Causa**: Backend no esta corriendo o CORS mal configurado.

**Solucion**:
1. Verificar que backend este en puerto 8081
2. Verificar `SecurityConfig.java` tiene CORS configurado

---

### Problema: Bearer token no se envia

**Sintoma**: Network tab muestra peticion sin header `Authorization`.

**Causas posibles**:
1. No hay token en localStorage
2. La URL esta en la lista de exclusion del interceptor
3. El interceptor no esta registrado

**Solucion**:

1. Verificar localStorage tiene `access_token`
2. Verificar `auth.interceptor.ts` no excluye la URL
3. Verificar `app.config.ts`:
```typescript
provideHttpClient(
  withInterceptors([authInterceptor])
)
```

---

### Problema: Dashboard muestra "Error al cargar perfil"

**Sintoma**: Dashboard carga pero muestra error.

**Diagnostico**:

1. Abrir Network tab
2. Buscar peticion a `/api/user/me`
3. Ver status code:
   - **401**: Token invalido o expirado
   - **403**: Usuario sin rol USER
   - **500**: Error en backend

**Soluciones**:
- 401: Hacer logout y login de nuevo
- 403: Verificar roles del usuario en Keycloak
- 500: Ver logs del backend

---

### Problema: Codigo temporal invalido o expirado

**Sintoma**: En callback, muestra error de codigo invalido.

**Causa**: El codigo temporal tiene TTL de 30 segundos.

**Solucion**:
1. Verificar que Redis este corriendo
2. Reintentar login mas rapidamente
3. Verificar logs del backend

---

### Problema: AuthGuard no funciona

**Sintoma**: Puedes acceder a `/dashboard` sin login.

**Causa**: Guard no esta registrado en la ruta.

**Solucion**: Verificar `app.routes.ts`:
```typescript
{
  path: 'dashboard',
  component: DashboardComponent,
  canActivate: [authGuard]  // Debe estar presente
}
```

---

### Problema: Signals no actualizan UI

**Sintoma**: Los datos cambian pero la UI no se actualiza.

**Causa**: No estas usando `.set()` para actualizar el signal.

**Solucion**:
```typescript
// Incorrecto
this.user = newUser;

// Correcto
this.user.set(newUser);
```

---

## Mejoras Futuras

### 1. Environment Files

Crear archivos de configuracion por entorno:

```typescript
// src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api'
};

// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiUrl: 'https://api.ejemplo.com/api'
};
```

### 2. Pagina de Admin

Componente solo accesible con rol ADMIN:

```typescript
{
  path: 'admin',
  component: AdminComponent,
  canActivate: [authGuard, roleGuard('ROLE_ADMIN')]
}
```

### 3. Loading Global

Servicio global de loading para todas las peticiones HTTP.

### 4. Toast Notifications

Implementar notificaciones para feedback del usuario.

### 5. LogService Condicional

Logs solo en desarrollo, no en produccion.

---

## Referencias

- [Angular Official Docs](https://angular.dev)
- [Angular Signals Guide](https://angular.dev/guide/signals)
- [Standalone Components](https://angular.dev/guide/components)
- [HttpClient Interceptors](https://angular.dev/guide/http/interceptors)
- [Functional Guards](https://angular.dev/guide/routing/common-router-tasks#preventing-unauthorized-access)

---

**Ir a**: [Documentacion Principal](README.md) | [Documentacion Backend](BACK_BFF.md)

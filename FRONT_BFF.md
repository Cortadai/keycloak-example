# Frontend - Angular BFF

Documentación completa del frontend implementado con Angular 21 (Standalone Components + Signals).

---

## 📋 Tabla de Contenidos

- [Arquitectura](#arquitectura)
- [Componentes Principales](#componentes-principales)
- [Servicios y State Management](#servicios-y-state-management)
- [Guards y Seguridad](#guards-y-seguridad)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)

---

## 🏗️ Arquitectura

### Patrón BFF en el Frontend

El frontend implementa el lado cliente del patrón BFF donde:

1. **NO almacena tokens** en localStorage/sessionStorage
2. **Cookies automáticas** enviadas con cada petición (`withCredentials: true`)
3. **Guards funcionales** protegen rutas sensibles
4. **Signals** para state management reactivo
5. **Standalone components** sin NgModules

### Diagrama de Flujo

```
┌─────────────────────────────────────────┐
│         Angular Application             │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │         AppConfig                 │ │
│  │  • Router                         │ │
│  │  • HttpClient                     │ │
│  │  • authInterceptor                │ │
│  └───────────────────────────────────┘ │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │      authInterceptor              │ │
│  │  • withCredentials: true          │ │
│  │  • Manejo 401/403                 │ │
│  └───────────────────────────────────┘ │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │      AuthService                  │ │
│  │  • checkAuthStatus()              │ │
│  │  • getUserProfile()               │ │
│  │  • login() / logout()             │ │
│  │  • Signals: isAuthenticated,      │ │
│  │    currentUser                    │ │
│  └───────────────────────────────────┘ │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │      authGuard                    │ │
│  │  • Verifica autenticación         │ │
│  │  • Redirect a /login si no auth   │ │
│  └───────────────────────────────────┘ │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │      Components                   │ │
│  │  • LoginComponent                 │ │
│  │  • DashboardComponent             │ │
│  └───────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

---

## 🔧 Componentes Principales

### 1. app.config.ts

**Ubicación**: `frontend/src/app/app.config.ts`

**Responsabilidad**: Configuración global de Angular.

```typescript
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor])  // ✅ Interceptor global
    )
  ]
};
```

**Características**:
- Router configurado con rutas
- HttpClient con interceptor `authInterceptor`
- No necesita NgModules (standalone)

---

### 2. app.routes.ts

**Ubicación**: `frontend/src/app/app.routes.ts`

**Responsabilidad**: Definición de rutas con protección.

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
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]  // ✅ Protegida con guard
  },
  {
    path: '**',
    redirectTo: '/login'
  }
];
```

**Rutas protegidas**:
- `/dashboard`: Requiere autenticación (authGuard)
- Cualquier ruta no definida → redirect a `/login`

---

### 3. LoginComponent

**Ubicación**: `frontend/src/app/features/login/login.component.ts`

**Responsabilidad**: Pantalla de login con diseño moderno.

```typescript
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="lock-icon">🔒</div>
        <h1>Keycloak Spring Demo</h1>
        <p class="subtitle">Patrón BFF con Cookies HttpOnly</p>

        <button class="login-button" (click)="login()">
          Login con Keycloak
        </button>

        <div class="security-info">
          <p>✅ Tokens seguros en cookies HttpOnly</p>
          <p>✅ Protección contra XSS y CSRF</p>
          <p>✅ OAuth2 con Keycloak</p>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }
    // ... más estilos
  `]
})
export class LoginComponent {
  private authService = inject(AuthService);

  login(): void {
    this.authService.login();
  }
}
```

**Características**:
- Standalone component
- Inline template y styles
- Diseño moderno con gradiente
- Un solo botón: "Login con Keycloak"

---

### 4. DashboardComponent

**Ubicación**: `frontend/src/app/features/dashboard/dashboard.component.ts`

**Responsabilidad**: Dashboard del usuario autenticado.

```typescript
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <!-- Navbar -->
      <nav class="navbar">
        <h1>Dashboard</h1>
        <button class="logout-button" (click)="logout()">
          Logout
        </button>
      </nav>

      <!-- Loading State -->
      @if (loading()) {
        <div class="loading">Cargando...</div>
      }

      <!-- Error State -->
      @else if (error()) {
        <div class="error">
          Error: {{ error() }}
        </div>
      }

      <!-- Success State - User Info -->
      @else if (user()) {
        <div class="content">
          <!-- Welcome Card -->
          <div class="welcome-card">
            <div class="avatar">{{ getInitials(user()!.name) }}</div>
            <h2>Bienvenido, {{ user()!.name }}!</h2>
            <p>Has iniciado sesión correctamente</p>
          </div>

          <!-- Info Cards -->
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
                  <span class="role-badge">{{ role }}</span>
                }
              </div>
            </div>
          </div>

          <!-- Security Info -->
          <div class="security-section">
            <h3>🔐 Seguridad BFF Activa</h3>
            <ul>
              <li>✅ JWT almacenado en cookie HttpOnly</li>
              <li>✅ JavaScript no puede acceder al token</li>
              <li>✅ SameSite=Strict protege contra CSRF</li>
              <li>✅ Roles validados en backend</li>
            </ul>
          </div>
        </div>
      }
    </div>
  `,
  styles: [/* estilos modernos */]
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);

  // Signals para state reactivo
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

  getInitials(name: string): string {
    return name.split(' ').map(n => n[0]).join('').toUpperCase();
  }
}
```

**Características**:
- Signals para state management
- Nueva sintaxis `@if` / `@else` / `@for` de Angular
- Loading, error y success states
- Responsive design (grid → column en mobile)
- Logout button en navbar

---

## 🔐 Servicios y State Management

### AuthService

**Ubicación**: `frontend/src/app/core/services/auth.service.ts`

**Responsabilidades**:
- Gestionar autenticación
- State management con Signals y BehaviorSubject
- Comunicación con backend BFF

```typescript
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private readonly API_URL = 'http://localhost:8081/api';

  // BehaviorSubject para streams reactivos
  private authStatusSubject = new BehaviorSubject<AuthStatus>({
    authenticated: false,
    username: null
  });
  public authStatus$ = this.authStatusSubject.asObservable();

  // Signals para state reactivo
  isAuthenticated = signal(false);
  currentUser = signal<User | null>(null);

  /**
   * Inicia el flujo OAuth2 con Keycloak
   * Redirige a backend que redirige a Keycloak
   */
  login(): void {
    window.location.href = `${this.API_URL}/auth/login`;
  }

  /**
   * Cierra sesión e invalida cookie
   */
  logout(): Observable<any> {
    return this.http.post(`${this.API_URL}/auth/logout`, {}).pipe(
      tap(() => {
        this.isAuthenticated.set(false);
        this.currentUser.set(null);
        this.authStatusSubject.next({
          authenticated: false,
          username: null
        });
      })
    );
  }

  /**
   * Verifica si hay sesión activa
   * Usado por authGuard antes de cada navegación
   */
  checkAuthStatus(): Observable<AuthStatus> {
    return this.http.get<AuthStatus>(`${this.API_URL}/auth/status`).pipe(
      tap(status => this.updateAuthState(status)),
      catchError(() => {
        this.updateAuthState({ authenticated: false, username: null });
        return of({ authenticated: false, username: null });
      })
    );
  }

  /**
   * Obtiene perfil completo del usuario
   */
  getUserProfile(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/user/me`).pipe(
      tap(user => this.currentUser.set(user))
    );
  }

  /**
   * Actualiza el state cuando cambia autenticación
   */
  private updateAuthState(status: AuthStatus): void {
    this.isAuthenticated.set(status.authenticated);
    this.authStatusSubject.next(status);
  }
}
```

**¿Por qué Signals + BehaviorSubject?**
- **Signals**: State local reactivo (Angular 16+)
- **BehaviorSubject**: Streams para subscripciones múltiples
- Ambos se complementan para diferentes use cases

**¿Por qué NO se almacena el token?**
- El token está en cookie HttpOnly
- JavaScript NO puede acceder a él
- Se envía automáticamente con cada petición

---

## 🛡️ Guards y Seguridad

### authGuard

**Ubicación**: `frontend/src/app/core/guards/auth.guard.ts`

**Responsabilidad**: Proteger rutas que requieren autenticación.

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.checkAuthStatus().pipe(
    map(status => {
      if (status.authenticated) {
        console.log('AuthGuard: Usuario autenticado, permitiendo acceso');
        return true;
      } else {
        console.log('AuthGuard: Usuario no autenticado, redirigiendo a /login');
        router.navigate(['/login']);
        return false;
      }
    }),
    catchError(() => {
      console.error('AuthGuard: Error verificando autenticación');
      router.navigate(['/login']);
      return of(false);
    })
  );
};
```

**Flujo del guard**:
1. Se ejecuta antes de cada navegación a ruta protegida
2. Llama a `authService.checkAuthStatus()`
3. Backend verifica cookie y devuelve `{ authenticated: true/false }`
4. Si `true`: permite navegación
5. Si `false`: redirect a `/login`

**¿Por qué verificar en backend?**
- La cookie HttpOnly NO es accesible desde JavaScript
- El frontend NO puede verificar validez del token
- Solo el backend puede validar el JWT

---

### roleGuard (Factory)

**Ubicación**: `frontend/src/app/core/guards/auth.guard.ts`

**Responsabilidad**: Verificar roles específicos.

```typescript
export function roleGuard(requiredRole: string): CanActivateFn {
  return (route, state) => {
    const authService = inject(AuthService);
    const router = inject(Router);

    return authService.getUserProfile().pipe(
      map(user => {
        if (user.roles.includes(requiredRole)) {
          return true;
        } else {
          console.error(`roleGuard: Usuario no tiene rol ${requiredRole}`);
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

**Ubicación**: `frontend/src/app/core/interceptors/auth.interceptor.ts`

**Responsabilidad**: Configurar peticiones HTTP con credentials.

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  // CRÍTICO: withCredentials permite enviar cookies
  const authReq = req.clone({
    withCredentials: true
  });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        console.log('No autenticado (401), redirigiendo a login');
        router.navigate(['/login']);
      } else if (error.status === 403) {
        console.log('Acceso denegado (403)');
        // Opcional: mostrar página de acceso denegado
      }
      return throwError(() => error);
    })
  );
};
```

**¿Por qué withCredentials: true?**
- Por defecto, `fetch` y `XMLHttpRequest` NO envían cookies cross-origin
- `withCredentials: true` indica al navegador que incluya cookies
- Sin esto, la cookie `ACCESS_TOKEN` NO se enviaría

**Manejo de errores**:
- **401 Unauthorized**: Redirect a login (sesión expirada o no autenticado)
- **403 Forbidden**: Usuario autenticado pero sin permisos

---

## 📊 Models

**Ubicación**: `frontend/src/app/core/models/user.model.ts`

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

export interface Authority {
  authority: string;
}

export interface LogoutResponse {
  message: string;
  redirect: string;
  logoutUrl?: string;
}
```

---

## 🧪 Testing

### Test 1: Verificar Pantalla de Login

```bash
cd frontend
npm start
```

Abrir `http://localhost:4200`:
- ✅ Debe mostrar pantalla de login
- ✅ Botón "Login con Keycloak"
- ✅ Diseño con gradiente morado

---

### Test 2: Flujo de Login

1. Click en "Login con Keycloak"
2. Verificar redirect a Keycloak (`localhost:9090`)
3. Autenticarse
4. Verificar redirect a `localhost:4200/dashboard`
5. Verificar cookie en DevTools:
   - Application → Cookies → `http://localhost:8081`
   - Buscar `ACCESS_TOKEN`
   - `HttpOnly` = ✅
   - `SameSite` = Strict

---

### Test 3: Dashboard Carga Datos

En el dashboard, verificar:
- ✅ Navbar con "Dashboard" y botón "Logout"
- ✅ Welcome card con avatar (iniciales)
- ✅ Info cards: Usuario, Email, Roles
- ✅ Roles mostrados como badges
- ✅ Sección de seguridad BFF

---

### Test 4: Verificar withCredentials en Network Tab

1. Abrir DevTools → Network
2. Filtrar por `user/me`
3. Click en la petición
4. Headers → Request Headers
5. Verificar:
```
Cookie: ACCESS_TOKEN=eyJhbGc...; JSESSIONID=...
```

✅ Cookie debe estar presente en el header

---

### Test 5: AuthGuard Protege Rutas

1. Hacer logout
2. Intentar navegar manualmente a `http://localhost:4200/dashboard`
3. Verificar redirect automático a `/login`
4. Console debe mostrar:
```
AuthGuard: Usuario no autenticado, redirigiendo a /login
```

---

### Test 6: Logout

1. Estando en dashboard, click en "Logout"
2. Verificar redirect a `/login`
3. Verificar en DevTools que cookie `ACCESS_TOKEN` desapareció o tiene `Max-Age=0`
4. Intentar acceder a `/dashboard` → debe redirigir a `/login`

---

### Test 7: Responsividad

1. DevTools → Toggle device toolbar (Ctrl+Shift+M)
2. Probar tamaños:
   - Mobile (375px): Grid de info-cards debe ser 1 columna
   - Tablet (768px): Grid debe ser 2 columnas
   - Desktop (1200px): Grid debe ser 3 columnas

---

## 🐛 Troubleshooting

### Problema: Error CORS

**Síntoma**:
```
Access to fetch at 'http://localhost:8081/api/auth/status' from origin
'http://localhost:4200' has been blocked by CORS policy: The value of
the 'Access-Control-Allow-Origin' header in the response must not be
the wildcard '*' when the request's credentials mode is 'include'.
```

**Causa**: Backend no permite `allowCredentials` o tiene CORS mal configurado.

**Solución**: Verificar backend (`SecurityConfig.java`):
```java
configuration.setAllowedOriginPatterns(Arrays.asList(
    "http://localhost:*",
    "http://127.0.0.1:*"
));
configuration.setAllowCredentials(true);  // CRÍTICO
```

---

### Problema: Cookie no se envía

**Síntoma**: Network tab muestra petición sin header `Cookie`.

**Causas posibles**:
1. `withCredentials: true` no está configurado
2. Cookie domain no coincide
3. Cookie SameSite bloqueando petición

**Solución**:

1. Verificar `authInterceptor.ts`:
```typescript
const authReq = req.clone({
  withCredentials: true  // Debe estar presente
});
```

2. Verificar que backend y frontend están en `localhost` (mismo dominio).

3. Verificar cookie en DevTools:
   - Domain: `localhost` ✅
   - SameSite: `Strict` ✅

---

### Problema: Dashboard muestra "Error al cargar perfil"

**Síntoma**: Dashboard carga pero muestra error.

**Diagnóstico**:

1. Abrir Network tab
2. Buscar petición a `/api/user/me`
3. Ver status code:
   - **401**: Cookie no está siendo enviada o es inválida
   - **403**: Usuario autenticado pero sin rol USER
   - **500**: Error en backend

**Soluciones**:
- 401: Verificar cookie y `withCredentials`
- 403: Verificar roles del usuario en Keycloak
- 500: Ver logs del backend

---

### Problema: AuthGuard no funciona

**Síntoma**: Puedes acceder a `/dashboard` sin login.

**Causa**: Guard no está registrado en la ruta.

**Solución**: Verificar `app.routes.ts`:
```typescript
{
  path: 'dashboard',
  component: DashboardComponent,
  canActivate: [authGuard]  // ✅ Debe estar presente
}
```

---

### Problema: Signals no actualizan UI

**Síntoma**: Los datos cambian pero la UI no se actualiza.

**Causa**: No estás usando `.set()` para actualizar el signal.

**Solución**:
```typescript
// ❌ Incorrecto
this.user = newUser;

// ✅ Correcto
this.user.set(newUser);
```

---

### Problema: JWT visible en document.cookie

**Síntoma**: En console, `document.cookie` muestra `ACCESS_TOKEN=...`.

**Causa**: Cookie NO tiene `HttpOnly=true`.

**Solución**: Verificar backend (`OAuth2LoginSuccessHandler`):
```java
cookie.setHttpOnly(true);  // Debe ser true
```

Si `HttpOnly=true`, entonces `document.cookie` NO debe mostrar `ACCESS_TOKEN`. Esto es correcto y esperado.

---

## 🚀 Mejoras Futuras

### 1. Environment Files

Crear archivos de configuración por entorno:

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

Usar en `AuthService`:
```typescript
private readonly API_URL = environment.apiUrl;
```

---

### 2. Refresh Token Automático

Interceptor que detecta 401 e intenta refresh transparente:

```typescript
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/refresh')) {
        // Intentar refresh
        return authService.refreshToken().pipe(
          switchMap(() => {
            // Retry petición original
            return next(req.clone({ withCredentials: true }));
          })
        );
      }
      return throwError(() => error);
    })
  );
};
```

---

### 3. Página de Admin

Componente solo accesible con rol ADMIN:

```typescript
{
  path: 'admin',
  component: AdminComponent,
  canActivate: [authGuard, roleGuard('ROLE_ADMIN')]
}
```

---

### 4. Loading Global

Servicio global de loading para todas las peticiones HTTP:

```typescript
@Injectable({
  providedIn: 'root'
})
export class LoadingService {
  loading = signal(false);
  private requestCount = 0;

  show(): void {
    this.requestCount++;
    this.loading.set(true);
  }

  hide(): void {
    this.requestCount--;
    if (this.requestCount <= 0) {
      this.requestCount = 0;
      this.loading.set(false);
    }
  }
}
```

---

### 5. Toast Notifications

Implementar notificaciones para feedback del usuario:
- Login exitoso
- Logout exitoso
- Errores de permisos

---

## 📖 Referencias

- [Angular Official Docs](https://angular.dev)
- [Angular Signals Guide](https://angular.dev/guide/signals)
- [Standalone Components](https://angular.dev/guide/components)
- [HttpClient withCredentials](https://angular.dev/api/common/http/HttpClient)
- [Functional Guards](https://angular.dev/guide/routing/common-router-tasks#preventing-unauthorized-access)
- [OWASP Cookie Security](https://owasp.org/www-community/controls/SecureCookieAttribute)

---

**Ir a**: [Documentación Principal](README.md) | [Documentación Backend](BACK_BFF.md)

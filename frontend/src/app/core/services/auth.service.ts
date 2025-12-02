import { Injectable, signal, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, BehaviorSubject, of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthStatus, User, LogoutResponse, TokenResponse, ExchangeRequest } from '../models/user.model';

/**
 * Claves para localStorage
 */
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'access_token',
  TOKEN_EXPIRY: 'token_expiry'
};

/**
 * Margen de tiempo antes de expiración para refresh proactivo (en ms)
 * 2 minutos antes de que expire
 */
const REFRESH_MARGIN_MS = 2 * 60 * 1000;

/**
 * Prefijo para logs del servicio de autenticación
 */
const LOG_PREFIX = '🔐 [AuthService]';

/**
 * Servicio de autenticación para el patrón BFF con Binding.
 *
 * Este servicio gestiona la autenticación usando Bearer tokens + Cookie:
 * - Almacena JWT con fingerprint en localStorage
 * - Cookie HttpOnly con hash del fingerprint (manejada por navegador)
 * - El refreshToken NUNCA está en el frontend (está en Redis)
 * - Implementa refresh proactivo + reactivo (en 401)
 *
 * El binding protege contra:
 * - XSS: Atacante roba JWT pero NO tiene cookie HttpOnly → BLOQUEADO
 * - CSRF: Atacante tiene cookie pero NO puede leer JWT → BLOQUEADO
 *
 * Flujo:
 * 1. Login redirige a Keycloak vía backend
 * 2. Backend recibe callback y genera código temporal
 * 3. Frontend intercambia código por JWT (backend setea cookie automáticamente)
 * 4. Todas las peticiones llevan header Authorization + cookie (withCredentials)
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8081/api';

  // BehaviorSubject para estado de autenticación
  private authStatusSubject = new BehaviorSubject<AuthStatus>({ authenticated: false });
  public authStatus$ = this.authStatusSubject.asObservable();

  // Signals para uso reactivo en templates
  public isAuthenticated = signal<boolean>(false);
  public currentUser = signal<string | null>(null);

  // Timer para refresh proactivo
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  // Flag para evitar múltiples refreshes simultáneos
  private isRefreshing = false;

  constructor(
    private http: HttpClient,
    private ngZone: NgZone
  ) {
    // Verificar token existente al iniciar
    this.initializeAuthState();
  }

  /**
   * Inicializa el estado de autenticación basándose en localStorage.
   */
  private initializeAuthState(): void {
    const token = this.getStoredToken();

    if (token && !this.isTokenExpired()) {
      // Hay token válido, configurar estado y timer
      this.isAuthenticated.set(true);
      this.scheduleTokenRefresh();

      // Verificar con el backend que el token sigue siendo válido
      this.checkAuthStatus().subscribe({
        next: (status) => this.updateAuthState(status),
        error: () => this.clearAuth()
      });
    } else if (token) {
      // Token expirado, intentar refresh
      this.refreshToken().subscribe({
        next: () => {},
        error: () => this.clearAuth()
      });
    }
  }

  /**
   * Inicia el flujo de login OAuth2 con Keycloak.
   * Redirige al backend que maneja OAuth2.
   */
  login(): void {
    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`${LOG_PREFIX} 🚀 PASO 1: Iniciando flujo OAuth2`);
    console.log(`   → Redirigiendo a: ${this.API_URL}/auth/login`);
    console.log(`   → El backend redirigirá a Keycloak para autenticación`);
    console.log('═══════════════════════════════════════════════════════════════');
    window.location.href = `${this.API_URL}/auth/login`;
  }

  /**
   * Intercambia el código temporal por un accessToken.
   * Llamado por el CallbackComponent después del redirect de OAuth2.
   *
   * @param code Código temporal de la URL
   * @returns Observable con la respuesta de tokens
   */
  exchangeCode(code: string): Observable<TokenResponse> {
    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`${LOG_PREFIX} 🔄 PASO 4: Intercambiando código temporal por JWT`);
    console.log(`   → Código recibido: ${code.substring(0, 8)}...`);
    console.log(`   → Endpoint: POST ${this.API_URL}/auth/exchange`);

    const request: ExchangeRequest = { code };

    return this.http.post<TokenResponse>(`${this.API_URL}/auth/exchange`, request).pipe(
      tap(response => {
        console.log(`   ✅ Intercambio exitoso!`);
        console.log(`   → AccessToken recibido: ${response.accessToken.substring(0, 20)}...`);
        console.log(`   → Expira en: ${response.expiresIn} segundos`);
        console.log(`   → Guardando en localStorage...`);

        this.storeToken(response.accessToken, response.expiresIn);
        this.isAuthenticated.set(true);

        console.log(`   ✅ Token almacenado en localStorage`);
        console.log(`   → Programando refresh proactivo...`);

        this.scheduleTokenRefresh();

        console.log('═══════════════════════════════════════════════════════════════');
      }),
      catchError(error => {
        console.error(`   ❌ Error intercambiando código:`, error);
        console.log('═══════════════════════════════════════════════════════════════');
        return throwError(() => error);
      })
    );
  }

  /**
   * Refresca el accessToken usando el refreshToken almacenado en backend.
   * Se llama proactivamente (timer) o reactivamente (401).
   *
   * @returns Observable con la respuesta de tokens
   */
  refreshToken(): Observable<TokenResponse> {
    if (this.isRefreshing) {
      console.log(`${LOG_PREFIX} ⏳ Refresh ya en progreso, esperando...`);
      return of({ accessToken: this.getStoredToken()!, expiresIn: 0 });
    }

    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`${LOG_PREFIX} 🔃 Solicitando refresh de token`);
    console.log(`   → El backend usará el RefreshToken de Redis`);

    this.isRefreshing = true;

    return this.http.post<TokenResponse>(`${this.API_URL}/auth/refresh`, {}).pipe(
      tap(response => {
        console.log(`   ✅ Refresh exitoso!`);
        console.log(`   → Nuevo AccessToken: ${response.accessToken.substring(0, 20)}...`);
        console.log(`   → Expira en: ${response.expiresIn} segundos`);

        this.storeToken(response.accessToken, response.expiresIn);
        this.scheduleTokenRefresh();
        this.isRefreshing = false;

        console.log('═══════════════════════════════════════════════════════════════');
      }),
      catchError(error => {
        console.error(`   ❌ Refresh falló:`, error.status, error.message);
        console.log(`   → Limpiando sesión local y redirigiendo a login`);
        console.log('═══════════════════════════════════════════════════════════════');

        this.isRefreshing = false;
        this.clearAuth();
        return throwError(() => error);
      })
    );
  }

  /**
   * Cierra la sesión del usuario.
   * El backend revoca el refresh token en Keycloak y Redis.
   */
  logout(): Observable<LogoutResponse> {
    console.log('═══════════════════════════════════════════════════════════════');
    console.log(`${LOG_PREFIX} 🚪 Iniciando logout`);
    console.log(`   → Backend revocará token en Keycloak`);
    console.log(`   → Backend eliminará RefreshToken de Redis`);

    return this.http.post<LogoutResponse>(`${this.API_URL}/auth/logout`, {}).pipe(
      tap(() => {
        console.log(`   ✅ Logout exitoso en backend`);
        this.clearAuth();
        console.log(`   ✅ Sesión local limpiada`);
        console.log('═══════════════════════════════════════════════════════════════');
      }),
      catchError(error => {
        console.warn(`   ⚠️ Error en logout backend:`, error);
        console.log(`   → Limpiando sesión local de todas formas`);
        this.clearAuth();
        console.log('═══════════════════════════════════════════════════════════════');
        return of({ success: true, message: 'Logout local completado' });
      })
    );
  }

  /**
   * Verifica si hay una sesión activa.
   * Envía el token al backend para validación.
   */
  checkAuthStatus(): Observable<AuthStatus> {
    console.log('───────────────────────────────────────────────────────────────');
    console.log(`${LOG_PREFIX} 🔍 Verificando estado de autenticación con backend`);

    return this.http.get<AuthStatus>(`${this.API_URL}/auth/status`).pipe(
      tap(status => {
        if (status.authenticated) {
          console.log(`   ✅ Token VÁLIDO - Usuario: ${status.username}`);
        } else {
          console.log(`   ❌ No autenticado`);
        }
        console.log('───────────────────────────────────────────────────────────────');
        this.updateAuthState(status);
      }),
      catchError(error => {
        console.error(`   ❌ Error verificando estado:`, error.status);
        console.log('───────────────────────────────────────────────────────────────');
        this.updateAuthState({ authenticated: false });
        return of({ authenticated: false });
      })
    );
  }

  /**
   * Obtiene la información completa del usuario autenticado.
   */
  getUserProfile(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/user/me`);
  }

  /**
   * Obtiene el token almacenado en localStorage.
   */
  getStoredToken(): string | null {
    return localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
  }

  /**
   * Verifica si el token está expirado.
   */
  isTokenExpired(): boolean {
    const expiry = localStorage.getItem(STORAGE_KEYS.TOKEN_EXPIRY);
    if (!expiry) return true;

    const expiryTime = parseInt(expiry, 10);
    return Date.now() >= expiryTime;
  }

  /**
   * Verifica si el token está próximo a expirar (para refresh proactivo).
   */
  isTokenNearExpiry(): boolean {
    const expiry = localStorage.getItem(STORAGE_KEYS.TOKEN_EXPIRY);
    if (!expiry) return true;

    const expiryTime = parseInt(expiry, 10);
    return Date.now() >= (expiryTime - REFRESH_MARGIN_MS);
  }

  /**
   * Almacena el token en localStorage.
   */
  private storeToken(token: string, expiresIn: number): void {
    localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, token);

    // Guardar timestamp de expiración
    const expiryTime = Date.now() + (expiresIn * 1000);
    localStorage.setItem(STORAGE_KEYS.TOKEN_EXPIRY, expiryTime.toString());
  }

  /**
   * Programa el refresh proactivo del token.
   * Se ejecuta 2 minutos antes de que expire.
   */
  private scheduleTokenRefresh(): void {
    // Cancelar timer anterior si existe
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }

    const expiry = localStorage.getItem(STORAGE_KEYS.TOKEN_EXPIRY);
    if (!expiry) return;

    const expiryTime = parseInt(expiry, 10);
    const refreshTime = expiryTime - REFRESH_MARGIN_MS - Date.now();

    if (refreshTime > 0) {
      const minutes = Math.round(refreshTime / 1000 / 60);
      console.log(`${LOG_PREFIX} ⏰ Refresh proactivo programado en ${minutes} minutos`);

      // Usar NgZone para que Angular detecte los cambios
      this.ngZone.runOutsideAngular(() => {
        this.refreshTimer = setTimeout(() => {
          this.ngZone.run(() => {
            console.log(`${LOG_PREFIX} ⏰ Ejecutando refresh proactivo (timer disparado)`);
            this.refreshToken().subscribe({
              next: () => console.log(`${LOG_PREFIX} ✅ Token refrescado proactivamente`),
              error: (err) => console.error(`${LOG_PREFIX} ❌ Error en refresh proactivo:`, err)
            });
          });
        }, refreshTime);
      });
    } else {
      console.log(`${LOG_PREFIX} ⚠️ Token ya expirado o próximo a expirar, no se programa refresh`);
    }
  }

  /**
   * Limpia la autenticación (logout local).
   */
  private clearAuth(): void {
    // Cancelar timer de refresh
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }

    // Limpiar localStorage
    localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
    localStorage.removeItem(STORAGE_KEYS.TOKEN_EXPIRY);

    // Actualizar estado
    this.isAuthenticated.set(false);
    this.currentUser.set(null);
    this.authStatusSubject.next({ authenticated: false });
  }

  /**
   * Actualiza el estado interno de autenticación.
   */
  private updateAuthState(status: AuthStatus): void {
    this.authStatusSubject.next(status);
    this.isAuthenticated.set(status.authenticated);
    this.currentUser.set(status.username || null);
  }

  /**
   * Getter síncrono del estado de autenticación.
   */
  get isAuthenticatedValue(): boolean {
    return this.authStatusSubject.value.authenticated;
  }
}

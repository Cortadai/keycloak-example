import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, BehaviorSubject } from 'rxjs';
import { AuthStatus, User, LogoutResponse } from '../models/user.model';

/**
 * Servicio de autenticación para el patrón BFF.
 *
 * Este servicio gestiona toda la autenticación usando cookies HttpOnly:
 * - NO almacena JWT en localStorage/sessionStorage
 * - Todas las peticiones usan withCredentials para enviar cookies
 * - El backend (BFF) gestiona las cookies de forma segura
 *
 * Características de seguridad:
 * - JWT nunca expuesto al JavaScript del frontend
 * - Cookies HttpOnly previenen XSS
 * - SameSite=Strict previene CSRF
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8081/api';

  // Signal para reactive state del usuario
  private authStatusSubject = new BehaviorSubject<AuthStatus>({ authenticated: false });
  public authStatus$ = this.authStatusSubject.asObservable();

  // Signal para facilitar uso en templates
  public isAuthenticated = signal<boolean>(false);
  public currentUser = signal<string | null>(null);

  constructor(private http: HttpClient) {
    // Verificar estado de autenticación al iniciar
    this.checkAuthStatus().subscribe({
      next: (status) => this.updateAuthState(status),
      error: () => this.updateAuthState({ authenticated: false })
    });
  }

  /**
   * Inicia el flujo de login OAuth2 con Keycloak.
   *
   * IMPORTANTE: Este método NO usa HttpClient, sino window.location
   * porque necesitamos una redirección completa del navegador a Keycloak.
   *
   * Flujo:
   * 1. Redirige a /api/auth/login
   * 2. Backend redirige a Keycloak
   * 3. Usuario se autentica en Keycloak
   * 4. Keycloak redirige de vuelta al backend
   * 5. Backend crea cookie HttpOnly
   * 6. Backend redirige a /dashboard en Angular
   */
  login(): void {
    window.location.href = `${this.API_URL}/auth/login`;
  }

  /**
   * Cierra la sesión del usuario.
   *
   * Esto invalida la cookie ACCESS_TOKEN en el backend.
   *
   * @returns Observable con la respuesta del logout
   */
  logout(): Observable<LogoutResponse> {
    return this.http.post<LogoutResponse>(`${this.API_URL}/auth/logout`, {}).pipe(
      tap(() => {
        this.updateAuthState({ authenticated: false });
      })
    );
  }

  /**
   * Verifica si hay una sesión activa.
   *
   * El backend verifica si hay una cookie válida.
   *
   * @returns Observable con el estado de autenticación
   */
  checkAuthStatus(): Observable<AuthStatus> {
    return this.http.get<AuthStatus>(`${this.API_URL}/auth/status`).pipe(
      tap(status => this.updateAuthState(status))
    );
  }

  /**
   * Obtiene la información completa del usuario autenticado.
   *
   * @returns Observable con los datos del usuario
   */
  getUserProfile(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/user/me`);
  }

  /**
   * Verifica si el usuario tiene un rol específico.
   *
   * @param role El rol a verificar (ej: 'ROLE_ADMIN')
   * @returns true si el usuario tiene el rol
   */
  hasRole(role: string): Observable<boolean> {
    return new Observable(observer => {
      this.checkAuthStatus().subscribe({
        next: (status) => {
          if (status.authenticated && status.authorities) {
            const hasRole = status.authorities.some(auth => auth.authority === role);
            observer.next(hasRole);
          } else {
            observer.next(false);
          }
          observer.complete();
        },
        error: () => {
          observer.next(false);
          observer.complete();
        }
      });
    });
  }

  /**
   * Actualiza el estado de autenticación interno.
   *
   * @param status Estado de autenticación del backend
   */
  private updateAuthState(status: AuthStatus): void {
    this.authStatusSubject.next(status);
    this.isAuthenticated.set(status.authenticated);
    this.currentUser.set(status.username || null);
  }

  /**
   * Obtiene el estado actual de autenticación de forma síncrona.
   *
   * @returns true si el usuario está autenticado
   */
  get isAuthenticatedValue(): boolean {
    return this.authStatusSubject.value.authenticated;
  }
}

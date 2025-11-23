import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { BehaviorSubject, filter, Observable, of } from 'rxjs';
import { User } from '../models/user.model';

/**
 * Servicio de autenticación para SPA con Authorization Code + PKCE.
 *
 * Este servicio usa angular-oauth2-oidc para implementar el flujo
 * Authorization Code con PKCE (Proof Key for Code Exchange).
 *
 * Diferencias con el patrón BFF:
 * - El SPA gestiona DIRECTAMENTE la autenticación con Keycloak
 * - JWT almacenado en localStorage (accesible desde JavaScript)
 * - NO usa cookies HttpOnly (menos seguro que BFF)
 * - PKCE protege contra ataques de interceptación del code
 *
 * Flujo de autenticación:
 * 1. Usuario hace click en "Login"
 * 2. SPA genera code_verifier y code_challenge (PKCE)
 * 3. SPA redirige al usuario a Keycloak con code_challenge
 * 4. Usuario se autentica en Keycloak
 * 5. Keycloak redirige de vuelta con authorization code
 * 6. SPA intercambia code + code_verifier por tokens
 * 7. SPA almacena tokens en localStorage
 * 8. SPA envía access_token en header Authorization
 *
 * Ventajas:
 * - Más simple que BFF (no necesita backend para login)
 * - Frontend tiene control total del flujo
 * - CORS simplificado
 *
 * Desventajas:
 * - Token accesible desde JavaScript (vulnerable a XSS)
 * - Menos seguro que BFF con cookies HttpOnly
 * - Refresh token debe manejarse con cuidado
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  // Signal para reactive state
  public isAuthenticated = signal<boolean>(false);
  public currentUser = signal<string | null>(null);
  public userRoles = signal<string[]>([]);

  // BehaviorSubject para compatibilidad con código existente
  private authStatusSubject = new BehaviorSubject<boolean>(false);
  public authStatus$ = this.authStatusSubject.asObservable();

  private readonly API_URL = 'http://localhost:8081/api';

  constructor(
    private oauthService: OAuthService,
    private router: Router,
    private http: HttpClient
  ) {
    this.configureOAuth();
    this.setupAuthFlow();
  }

  /**
   * Configuración de OAuth2/OIDC para Keycloak.
   *
   * IMPORTANTE:
   * - responseType: 'code' → Authorization Code Flow
   * - usePkce: true → Habilita PKCE
   * - showDebugInformation: true → Útil para desarrollo
   */
  private configureOAuth(): void {
    const authConfig: AuthConfig = {
      // URL base de Keycloak
      issuer: 'http://localhost:9090/realms/mi-realm',

      // Client ID (debe ser público en Keycloak)
      clientId: 'spring-boot-angular',

      // URL de redirección después del login
      redirectUri: window.location.origin,

      // Usar Authorization Code Flow (no Implicit)
      responseType: 'code',

      // Scopes solicitados
      scope: 'openid profile email',

      // Mostrar logs en desarrollo
      showDebugInformation: true,

      // Validar el issuer del token
      strictDiscoveryDocumentValidation: true,

      // Refresh token automático
      sessionChecksEnabled: false,

      // PKCE se habilita automáticamente para clientes públicos
      // (cuando no hay requireHttps o oidc = true)
      oidc: true,
    };

    this.oauthService.configure(authConfig);
  }

  /**
   * Configura el flujo de autenticación automático.
   *
   * Este método:
   * 1. Carga la configuración de Discovery de Keycloak
   * 2. Intenta hacer login automático si hay tokens guardados
   * 3. Escucha eventos de autenticación
   */
  private setupAuthFlow(): void {
    // Cargar configuración de Discovery desde Keycloak
    this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      // Verificar si hay una sesión activa
      if (this.oauthService.hasValidAccessToken()) {
        this.updateAuthState(true);
      }
    });

    // Escuchar eventos de tokens
    this.oauthService.events
      .pipe(filter((e) => e.type === 'token_received'))
      .subscribe(() => {
        this.updateAuthState(true);
      });

    this.oauthService.events
      .pipe(filter((e) => e.type === 'logout'))
      .subscribe(() => {
        this.updateAuthState(false);
      });
  }

  /**
   * Inicia el flujo de login con Keycloak.
   *
   * Este método:
   * 1. Genera code_verifier y code_challenge (PKCE)
   * 2. Redirige al usuario a la página de login de Keycloak
   * 3. Keycloak redirige de vuelta con el authorization code
   * 4. angular-oauth2-oidc automáticamente intercambia el code por tokens
   */
  login(): void {
    this.oauthService.initCodeFlow();
  }

  /**
   * Cierra la sesión del usuario.
   *
   * Este método:
   * 1. Revoca los tokens en Keycloak (opcional)
   * 2. Limpia los tokens del localStorage
   * 3. Redirige al usuario a la página de login
   *
   * @returns Observable que completa cuando se finaliza el logout
   */
  logout(): Observable<void> {
    return new Observable(observer => {
      this.oauthService.logOut();
      this.updateAuthState(false);
      this.router.navigate(['/login']);
      observer.next();
      observer.complete();
    });
  }

  /**
   * Obtiene el access token actual.
   *
   * @returns El access token o null si no hay sesión
   */
  getAccessToken(): string | null {
    return this.oauthService.getAccessToken();
  }

  /**
   * Obtiene los claims del ID token.
   *
   * El ID token contiene información del usuario:
   * - preferred_username
   * - email
   * - name
   * - realm_access.roles
   *
   * @returns Los claims del token o null
   */
  getIdentityClaims(): any {
    return this.oauthService.getIdentityClaims();
  }

  /**
   * Verifica si el usuario tiene un rol específico.
   *
   * Los roles están en el campo realm_access.roles del token.
   *
   * @param role El rol a verificar (ej: 'user', 'admin')
   * @returns true si el usuario tiene el rol
   */
  hasRole(role: string): boolean {
    const claims: any = this.getIdentityClaims();
    if (!claims || !claims.realm_access || !claims.realm_access.roles) {
      return false;
    }
    return claims.realm_access.roles.includes(role);
  }

  /**
   * Refresca el access token usando el refresh token.
   *
   * Útil cuando el access token expira pero el refresh token sigue válido.
   *
   * @returns Promise que resuelve cuando el token se ha refrescado
   */
  async refreshToken(): Promise<void> {
    try {
      await this.oauthService.refreshToken();
      this.updateAuthState(true);
    } catch (error) {
      console.error('Error refrescando token:', error);
      this.logout();
    }
  }

  /**
   * Actualiza el estado de autenticación interno.
   *
   * @param authenticated Estado de autenticación
   */
  private updateAuthState(authenticated: boolean): void {
    this.authStatusSubject.next(authenticated);
    this.isAuthenticated.set(authenticated);

    if (authenticated) {
      const claims: any = this.getIdentityClaims();
      this.currentUser.set(claims?.preferred_username || null);

      // Extraer roles
      const roles = claims?.realm_access?.roles || [];
      this.userRoles.set(roles);
    } else {
      this.currentUser.set(null);
      this.userRoles.set([]);
    }
  }

  /**
   * Obtiene el estado actual de autenticación de forma síncrona.
   *
   * @returns true si el usuario está autenticado
   */
  get isAuthenticatedValue(): boolean {
    return this.oauthService.hasValidAccessToken();
  }

  /**
   * Obtiene el perfil completo del usuario desde el backend.
   *
   * Este método hace una petición HTTP al endpoint /api/user/me
   * con el token JWT en el header Authorization.
   *
   * @returns Observable con los datos del usuario
   */
  getUserProfile(): Observable<User> {
    return this.http.get<User>(`${this.API_URL}/user/me`);
  }
}

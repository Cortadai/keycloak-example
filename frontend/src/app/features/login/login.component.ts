import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Componente de login.
 *
 * Pantalla simple con un botón para iniciar el flujo OAuth2 con Keycloak.
 *
 * No hay formularios de login porque la autenticación se hace
 * completamente en Keycloak (patrón SPA + PKCE).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo">
          <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
          </svg>
        </div>

        <h1>Keycloak Spring Demo</h1>
        <p class="subtitle">Patrón SPA con PKCE</p>

        <button class="login-button" (click)="login()">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/>
            <polyline points="10 17 15 12 10 7"/>
            <line x1="15" y1="12" x2="3" y2="12"/>
          </svg>
          Login con Keycloak
        </button>

        <div class="info">
          <p>Al hacer click, serás redirigido a Keycloak para autenticarte de forma segura.</p>
          <p class="security-note">🔒 Autenticación segura con PKCE (Proof Key for Code Exchange).</p>
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
      padding: 20px;
    }

    .login-card {
      background: white;
      border-radius: 16px;
      padding: 48px;
      max-width: 450px;
      width: 100%;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
      text-align: center;
    }

    .logo {
      color: #667eea;
      margin-bottom: 24px;
      display: flex;
      justify-content: center;
    }

    h1 {
      margin: 0 0 8px 0;
      color: #1a202c;
      font-size: 28px;
      font-weight: 600;
    }

    .subtitle {
      margin: 0 0 32px 0;
      color: #718096;
      font-size: 14px;
    }

    .login-button {
      width: 100%;
      padding: 16px 24px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      border-radius: 12px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 12px;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }

    .login-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
    }

    .login-button:active {
      transform: translateY(0);
    }

    .info {
      margin-top: 32px;
      padding-top: 32px;
      border-top: 1px solid #e2e8f0;
    }

    .info p {
      margin: 0 0 12px 0;
      color: #4a5568;
      font-size: 14px;
      line-height: 1.6;
    }

    .security-note {
      background: #f7fafc;
      padding: 12px;
      border-radius: 8px;
      border-left: 4px solid #48bb78;
      color: #2d3748;
      font-size: 13px;
    }

    @media (max-width: 600px) {
      .login-card {
        padding: 32px 24px;
      }

      h1 {
        font-size: 24px;
      }
    }
  `]
})
export class LoginComponent implements OnInit {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    console.log('[LoginComponent] ngOnInit - Verificando autenticación...');
    // Si el usuario ya está autenticado, redirigir al dashboard
    const isAuth = this.authService.isAuthenticatedValue;
    console.log('[LoginComponent] ¿Usuario autenticado?', isAuth);
    if (isAuth) {
      console.log('[LoginComponent] Usuario ya autenticado, redirigiendo al dashboard...');
      this.router.navigate(['/dashboard']);
    }
  }

  /**
   * Inicia el flujo de login con Keycloak.
   *
   * Esto inicia el Authorization Code Flow con PKCE.
   */
  login(): void {
    console.log('[LoginComponent] Botón de login clickeado');
    this.authService.login();
  }
}

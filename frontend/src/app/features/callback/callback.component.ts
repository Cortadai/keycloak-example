import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/**
 * Componente que maneja el callback después del login OAuth2.
 *
 * Flujo:
 * 1. Usuario completa autenticación en Keycloak
 * 2. Backend genera código temporal y redirige aquí con ?code=xxx
 * 3. Este componente intercambia el código por accessToken
 * 4. Redirige al dashboard o muestra error
 */
@Component({
  selector: 'app-callback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="callback-container">
      <div class="callback-card">
        @if (loading()) {
          <div class="loading-state">
            <div class="spinner"></div>
            <h2>Completando autenticación...</h2>
            <p>Por favor espere mientras verificamos sus credenciales</p>
          </div>
        }

        @if (error()) {
          <div class="error-state">
            <div class="error-icon">⚠️</div>
            <h2>Error de autenticación</h2>
            <p>{{ error() }}</p>
            <button class="retry-button" (click)="goToLogin()">
              Volver al login
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .callback-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }

    .callback-card {
      background: white;
      border-radius: 16px;
      padding: 48px;
      text-align: center;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
      max-width: 400px;
      width: 90%;
    }

    .loading-state h2 {
      margin: 24px 0 8px;
      color: #1a1a2e;
      font-size: 1.5rem;
    }

    .loading-state p {
      color: #666;
      margin: 0;
    }

    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #e0e0e0;
      border-top-color: #667eea;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto;
    }

    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }

    .error-state {
      color: #dc3545;
    }

    .error-icon {
      font-size: 48px;
      margin-bottom: 16px;
    }

    .error-state h2 {
      margin: 0 0 8px;
      color: #dc3545;
    }

    .error-state p {
      color: #666;
      margin: 0 0 24px;
    }

    .retry-button {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      padding: 12px 32px;
      border-radius: 8px;
      font-size: 1rem;
      cursor: pointer;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .retry-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
    }
  `]
})
export class CallbackComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  loading = signal(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.processCallback();
  }

  /**
   * Procesa el callback OAuth2.
   * Extrae el código de la URL y lo intercambia por el token.
   */
  private processCallback(): void {
    // Obtener código temporal de los query params
    this.route.queryParams.subscribe(params => {
      const code = params['code'];
      const errorParam = params['error'];

      // Si hay error en los params (viene de Keycloak o backend)
      if (errorParam) {
        this.handleError(this.getErrorMessage(errorParam));
        return;
      }

      // Si no hay código, error
      if (!code) {
        this.handleError('No se recibió código de autenticación');
        return;
      }

      // Intercambiar código por token
      this.exchangeCode(code);
    });
  }

  /**
   * Intercambia el código temporal por el accessToken.
   */
  private exchangeCode(code: string): void {
    this.authService.exchangeCode(code).subscribe({
      next: () => {
        // Éxito: redirigir al dashboard
        console.log('Autenticación completada, redirigiendo al dashboard');
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        console.error('Error en exchangeCode:', err);
        this.handleError(this.getErrorFromResponse(err));
      }
    });
  }

  /**
   * Maneja un error mostrándolo en la UI.
   */
  private handleError(message: string): void {
    this.loading.set(false);
    this.error.set(message);
  }

  /**
   * Obtiene mensaje de error legible.
   */
  private getErrorMessage(errorCode: string): string {
    const errorMessages: Record<string, string> = {
      'auth_failed': 'La autenticación con Keycloak falló',
      'no_token': 'No se pudo obtener el token de acceso',
      'access_denied': 'Acceso denegado',
      'invalid_request': 'Solicitud inválida'
    };
    return errorMessages[errorCode] || `Error de autenticación: ${errorCode}`;
  }

  /**
   * Extrae mensaje de error de la respuesta HTTP.
   */
  private getErrorFromResponse(err: any): string {
    if (err.error?.message) {
      return err.error.message;
    }
    if (err.status === 401) {
      return 'El código de autenticación expiró o es inválido';
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor';
    }
    return 'Error desconocido durante la autenticación';
  }

  /**
   * Navega a la página de login.
   */
  goToLogin(): void {
    this.router.navigate(['/login']);
  }
}

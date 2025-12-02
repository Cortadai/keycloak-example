import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/user.model';

/**
 * Componente del dashboard del usuario.
 *
 * Muestra información del usuario autenticado y permite hacer logout.
 * Esta ruta está protegida por el authGuard.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <nav class="navbar">
        <div class="nav-content">
          <h2>Dashboard</h2>
          <button class="logout-button" (click)="logout()">
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
            Logout
          </button>
        </div>
      </nav>

      <div class="content">
        @if (loading()) {
          <div class="loading">
            <div class="spinner"></div>
            <p>Cargando información del usuario...</p>
          </div>
        } @else if (error()) {
          <div class="error-card">
            <h3>Error al cargar perfil</h3>
            <p>{{ error() }}</p>
            <button (click)="loadUserProfile()">Reintentar</button>
          </div>
        } @else if (user()) {
          <div class="welcome-card">
            <div class="avatar">
              {{ getInitials(user()!.name) }}
            </div>
            <h1>Bienvenido, {{ user()!.name }}!</h1>
            <p class="subtitle">Has iniciado sesión correctamente usando Keycloak</p>
          </div>

          <div class="info-grid">
            <div class="info-card">
              <div class="info-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </div>
              <h3>Usuario</h3>
              <p>{{ user()!.username }}</p>
            </div>

            <div class="info-card">
              <div class="info-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
                  <polyline points="22,6 12,13 2,6"/>
                </svg>
              </div>
              <h3>Email</h3>
              <p>{{ user()!.email }}</p>
            </div>

            <div class="info-card">
              <div class="info-icon">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </div>
              <h3>Roles</h3>
              <div class="roles">
                @for (role of user()!.roles; track role) {
                  <span class="role-badge" [class.admin]="role.includes('ADMIN')">
                    {{ formatRole(role) }}
                  </span>
                }
              </div>
            </div>
          </div>

          <div class="security-info">
            <h3>🔒 Información de Seguridad BFF</h3>
            <ul>
              <li>Access Token en localStorage + header Authorization Bearer</li>
              <li>Refresh Token seguro en Redis (nunca expuesto al frontend)</li>
              <li>Refresh proactivo antes de expirar + reactivo en 401</li>
              <li>Sesión única por usuario (nuevo login invalida el anterior)</li>
              <li>Autenticación OAuth2 gestionada por Keycloak</li>
            </ul>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .dashboard-container {
      min-height: 100vh;
      background: #f7fafc;
    }

    .navbar {
      background: white;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      position: sticky;
      top: 0;
      z-index: 10;
    }

    .nav-content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 16px 24px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .nav-content h2 {
      margin: 0;
      color: #1a202c;
      font-size: 24px;
      font-weight: 600;
    }

    .logout-button {
      padding: 10px 20px;
      background: #e53e3e;
      color: white;
      border: none;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .logout-button:hover {
      background: #c53030;
      transform: translateY(-1px);
    }

    .content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 48px 24px;
    }

    .loading {
      text-align: center;
      padding: 60px 20px;
    }

    .spinner {
      width: 50px;
      height: 50px;
      border: 4px solid #e2e8f0;
      border-top-color: #667eea;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin: 0 auto 20px;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .error-card {
      background: white;
      padding: 40px;
      border-radius: 12px;
      text-align: center;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    .error-card h3 {
      color: #e53e3e;
      margin: 0 0 12px 0;
    }

    .welcome-card {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      padding: 48px;
      border-radius: 16px;
      text-align: center;
      color: white;
      margin-bottom: 32px;
      box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
    }

    .avatar {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 32px;
      font-weight: 600;
      margin: 0 auto 24px;
      border: 4px solid rgba(255, 255, 255, 0.3);
    }

    .welcome-card h1 {
      margin: 0 0 8px 0;
      font-size: 32px;
      font-weight: 600;
    }

    .subtitle {
      margin: 0;
      opacity: 0.9;
      font-size: 16px;
    }

    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 24px;
      margin-bottom: 32px;
    }

    .info-card {
      background: white;
      padding: 32px;
      border-radius: 12px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    .info-icon {
      color: #667eea;
      margin-bottom: 16px;
    }

    .info-card h3 {
      margin: 0 0 8px 0;
      color: #4a5568;
      font-size: 14px;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }

    .info-card p {
      margin: 0;
      color: #1a202c;
      font-size: 18px;
      font-weight: 600;
    }

    .roles {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .role-badge {
      display: inline-block;
      padding: 6px 12px;
      background: #edf2f7;
      color: #4a5568;
      border-radius: 6px;
      font-size: 14px;
      font-weight: 500;
    }

    .role-badge.admin {
      background: #fed7d7;
      color: #c53030;
    }

    .security-info {
      background: white;
      padding: 32px;
      border-radius: 12px;
      box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
      border-left: 4px solid #48bb78;
    }

    .security-info h3 {
      margin: 0 0 16px 0;
      color: #1a202c;
      font-size: 20px;
    }

    .security-info ul {
      margin: 0;
      padding-left: 24px;
      color: #4a5568;
      line-height: 1.8;
    }

    .security-info li {
      margin-bottom: 8px;
    }

    @media (max-width: 768px) {
      .nav-content {
        flex-direction: column;
        gap: 16px;
        align-items: flex-start;
      }

      .welcome-card {
        padding: 32px 24px;
      }

      .welcome-card h1 {
        font-size: 24px;
      }

      .info-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class DashboardComponent implements OnInit {
  user = signal<User | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadUserProfile();
  }

  loadUserProfile(): void {
    this.loading.set(true);
    this.error.set(null);

    this.authService.getUserProfile().subscribe({
      next: (user) => {
        this.user.set(user);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Error cargando perfil de usuario:', err);
        this.error.set('No se pudo cargar la información del usuario');
        this.loading.set(false);
      }
    });
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/login']);
      },
      error: (err) => {
        console.error('Error durante logout:', err);
        // Redirigir a login de todos modos
        this.router.navigate(['/login']);
      }
    });
  }

  getInitials(name: string): string {
    if (!name) return '?';
    return name
      .split(' ')
      .map(n => n[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  formatRole(role: string): string {
    // Convertir "ROLE_USER" a "User"
    return role.replace('ROLE_', '').replace('SCOPE_', '');
  }
}

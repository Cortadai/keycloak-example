/**
 * Modelo del usuario autenticado
 */
export interface User {
  username: string;
  email: string;
  name: string;
  roles: string[];
  authenticated: boolean;
  message?: string;
}

/**
 * Estado de autenticación
 */
export interface AuthStatus {
  authenticated: boolean;
  username?: string;
  authorities?: Authority[];
  message?: string;
}

/**
 * Autoridad/Rol del usuario
 */
export interface Authority {
  authority: string;
}

/**
 * Respuesta de logout
 */
export interface LogoutResponse {
  success: boolean;
  message: string;
}

/**
 * Respuesta del endpoint de intercambio de código temporal
 */
export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
}

/**
 * Request para intercambiar código temporal
 */
export interface ExchangeRequest {
  code: string;
}

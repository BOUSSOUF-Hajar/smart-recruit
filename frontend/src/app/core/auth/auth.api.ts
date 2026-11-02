import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  User,
} from './auth.models';

/** Endpoints d'authentification (docs/api.md §2) ; un appel HTTP par méthode, sans logique. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/register', request);
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', request);
  }

  /** Le cookie `refresh_token` est envoyé automatiquement : même origine, chemin `/api/v1/auth`. */
  refresh(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/v1/auth/refresh', null);
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', null);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password', request);
  }

  me(): Observable<User> {
    return this.http.get<User>('/api/v1/me');
  }
}

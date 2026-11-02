import { Injectable, computed, signal } from '@angular/core';
import { AuthResponse, Role, User } from './auth.models';

interface Session {
  accessToken: string;
  user: User;
}

/**
 * État de la session, en signals. L'access token ne vit qu'en mémoire : jamais dans le
 * localStorage, où un script injecté (XSS) pourrait le lire. Au rechargement de la page,
 * la session est restaurée grâce au cookie HttpOnly du refresh token.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly session = signal<Session | null>(null);

  readonly user = computed(() => this.session()?.user ?? null);
  readonly accessToken = computed(() => this.session()?.accessToken ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly role = computed<Role | null>(() => this.user()?.role ?? null);
  readonly mustChangePassword = computed(() => this.user()?.mustChangePassword ?? false);
  readonly displayName = computed(() => {
    const user = this.user();
    return user ? `${user.firstName} ${user.lastName}` : '';
  });

  start(response: AuthResponse): void {
    this.session.set({ accessToken: response.accessToken, user: response.user });
  }

  clear(): void {
    this.session.set(null);
  }
}

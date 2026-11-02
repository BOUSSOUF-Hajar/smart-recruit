import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  Observable,
  catchError,
  finalize,
  from,
  map,
  of,
  shareReplay,
  switchMap,
  tap,
  firstValueFrom,
} from 'rxjs';
import { NotificationService } from '../errors/notification.service';
import { AuthApi } from './auth.api';
import { AuthResponse, ChangePasswordRequest, LoginRequest, RegisterRequest } from './auth.models';
import { AuthStore } from './auth.store';

/** Nom du verrou partagé par tous les onglets de l'application (API Web Locks). */
export const REFRESH_LOCK = 'smartrecruit-refresh';

/**
 * Cycle de vie de la session (architecture frontend §4) : ouverture, renouvellement,
 * restauration au démarrage et fermeture. Les écrans passent par ce service, jamais par l'API directement.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthApi);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  /** Renouvellement en cours, partagé par toutes les requêtes qui ont expiré en même temps. */
  private refreshInFlight: Observable<string> | null = null;

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.api.login(request).pipe(tap((response) => this.store.start(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.api.register(request).pipe(tap((response) => this.store.start(response)));
  }

  /**
   * Obtient un nouvel access token grâce au cookie. Un seul appel à la fois :
   * - dans l'onglet, les requêtes expirées simultanément partagent le même renouvellement ;
   * - entre onglets, le verrou Web Locks évite que deux onglets présentent le même refresh token,
   *   ce que le backend prendrait pour un vol (révocation de toutes les sessions, US-08).
   */
  refreshAccessToken(): Observable<string> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = from(
        this.withRefreshLock(() => firstValueFrom(this.api.refresh())),
      ).pipe(
        tap((response) => this.store.start(response)),
        map((response) => response.accessToken),
        finalize(() => (this.refreshInFlight = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight;
  }

  /**
   * Au démarrage : restaure la session si le cookie est encore valide. N'échoue jamais,
   * un visiteur sans cookie est simplement anonyme.
   */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refreshAccessToken().pipe(
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  /**
   * US-11. Après le changement, la session courante est conservée côté serveur : un renouvellement
   * fournit un token sans le marqueur « mot de passe à changer ».
   */
  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.api.changePassword(request).pipe(
      switchMap(() => this.refreshAccessToken()),
      map(() => undefined),
    );
  }

  /** US-09. La session locale est effacée même si l'appel échoue (serveur injoignable). */
  logout(): Observable<void> {
    return this.api.logout().pipe(
      catchError(() => of(undefined)),
      tap(() => this.endSession('/login')),
      map(() => undefined),
    );
  }

  /** Renouvellement impossible : retour à la connexion, en revenant ensuite à la page courante. */
  sessionExpired(): void {
    if (!this.store.isAuthenticated()) {
      return;
    }
    this.notifications.error($localize`Votre session a expiré. Reconnectez-vous.`);
    const returnUrl = this.router.url;
    this.store.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl } });
  }

  private endSession(destination: string): void {
    this.store.clear();
    void this.router.navigateByUrl(destination);
  }

  private withRefreshLock<T>(task: () => Promise<T>): Promise<T> {
    // Navigateurs anciens et jsdom (tests) n'ont pas Web Locks : un seul onglet est alors protégé.
    if (typeof navigator !== 'undefined' && 'locks' in navigator && navigator.locks) {
      return navigator.locks.request(REFRESH_LOCK, task);
    }
    return task();
  }
}

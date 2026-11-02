import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { AuthStore } from '../auth/auth.store';
import { isOwnApi } from './correlation.interceptor';
import { isSessionEndpoint } from './auth-endpoints';

/**
 * Sur `401 AUTH_TOKEN_EXPIRED`, renouvelle l'access token puis rejoue la requête une seule fois.
 * Placé avant `authInterceptor` : la requête rejouée repasse par lui et reçoit le nouveau token.
 * <p>
 * Tout autre 401 sur une requête authentifiée (token refusé, session révoquée) ferme la session.
 */
export const refreshInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url) || isSessionEndpoint(request.url)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const store = inject(AuthStore);

  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        !(error instanceof HttpErrorResponse) ||
        error.status !== 401 ||
        !store.isAuthenticated()
      ) {
        return throwError(() => error);
      }
      if (codeOf(error) !== 'AUTH_TOKEN_EXPIRED') {
        auth.sessionExpired();
        return throwError(() => error);
      }
      return auth.refreshAccessToken().pipe(
        catchError(() => {
          auth.sessionExpired();
          // L'appelant reçoit l'erreur d'origine, pas celle du renouvellement
          return throwError(() => error);
        }),
        switchMap(() => next(request)),
      );
    }),
  );
};

function codeOf(error: HttpErrorResponse): string | undefined {
  const body: unknown = error.error;
  if (
    typeof body === 'object' &&
    body !== null &&
    'code' in body &&
    typeof body.code === 'string'
  ) {
    return body.code;
  }
  return undefined;
}

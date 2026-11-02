import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ApiError, NETWORK_ERROR, toApiError } from '../errors/api-error';
import { NotificationService } from '../errors/notification.service';

/**
 * Transforme toute erreur HTTP en {@link ApiError} typée, et affiche une notification générique
 * pour les erreurs qu'aucun écran ne sait traiter : serveur injoignable, 5xx, 429, 403.
 *
 * Les erreurs propres à un écran (400, 404, 409, 422) lui sont laissées : il les affiche
 * sous le bon champ ou dans un bandeau, avec un message adapté.
 *
 * Cas particulier : `403 PASSWORD_CHANGE_REQUIRED` mène à l'écran de changement de mot de passe (US-11).
 */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  const router = inject(Router);

  return next(request).pipe(
    catchError((response: unknown) => {
      if (!(response instanceof HttpErrorResponse)) {
        return throwError(() => response);
      }
      const error = toApiError(response);
      if (error.code === PASSWORD_CHANGE_REQUIRED) {
        void router.navigateByUrl('/account/password');
        return throwError(() => error);
      }
      const message = globalMessageFor(error);
      if (message) {
        notifications.error(message);
      }
      return throwError(() => error);
    }),
  );
};

export const PASSWORD_CHANGE_REQUIRED = 'PASSWORD_CHANGE_REQUIRED';

/** Message de notification globale, ou `null` si l'écran doit gérer l'erreur lui-même. */
export function globalMessageFor(error: ApiError): string | null {
  if (error.code === NETWORK_ERROR) {
    return $localize`Serveur injoignable. Vérifiez votre connexion puis réessayez.`;
  }
  if (error.status >= 500) {
    const reference = error.correlationId ? ` (réf. ${error.correlationId})` : '';
    return $localize`Une erreur est survenue côté serveur${reference}:reference:.`;
  }
  if (error.status === 429) {
    return $localize`Trop de requêtes. Patientez un instant avant de réessayer.`;
  }
  if (error.status === 403) {
    return $localize`Vous n'avez pas accès à cette action.`;
  }
  return null;
}

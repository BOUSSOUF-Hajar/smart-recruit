import { ErrorHandler, Injectable, inject, isDevMode } from '@angular/core';
import { NotificationService } from './notification.service';
import { isApiError } from './api-error';

/**
 * Filet de sécurité pour les erreurs JavaScript non prévues : notification générique,
 * et trace dans la console en développement seulement.
 */
@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  private readonly notifications = inject(NotificationService);

  handleError(error: unknown): void {
    // Les erreurs HTTP non interceptées par un écran ont déjà été notifiées par l'intercepteur
    if (isApiError(error)) {
      return;
    }
    if (isDevMode()) {
      console.error(error);
    }
    this.notifications.error($localize`Une erreur inattendue est survenue. Rechargez la page.`);
  }
}

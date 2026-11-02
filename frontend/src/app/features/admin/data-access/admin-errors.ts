import { NotificationService } from '../../../core/errors/notification.service';
import { isApiError } from '../../../core/errors/api-error';

/** Message de l'API s'il existe (il est rédigé pour l'utilisateur), sinon un message générique. */
export function notifyFailure(notifications: NotificationService, error: unknown): void {
  if (isApiError(error) && error.code === 'CONCURRENT_MODIFICATION') {
    notifications.error(
      $localize`Quelqu'un a modifié cet élément entre-temps : rechargez la page.`,
    );
  } else if (isApiError(error) && error.detail) {
    notifications.error(error.detail);
  } else {
    notifications.error($localize`L'action n'a pas pu aboutir. Réessayez.`);
  }
}

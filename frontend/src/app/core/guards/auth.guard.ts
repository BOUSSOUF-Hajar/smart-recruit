import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthStore } from '../auth/auth.store';

/**
 * Espace réservé aux utilisateurs connectés ; sinon connexion, puis retour à la page demandée,
 * paramètres de requête compris (filtres d'une liste, par exemple).
 */
export const authGuard: CanMatchFn = (_route, segments, current) => {
  if (inject(AuthStore).isAuthenticated()) {
    return true;
  }
  const router = inject(Router);
  const requested = router.createUrlTree(['/', ...segments.map((segment) => segment.path)], {
    queryParams: current?.queryParams,
  });
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: router.serializeUrl(requested) },
  });
};

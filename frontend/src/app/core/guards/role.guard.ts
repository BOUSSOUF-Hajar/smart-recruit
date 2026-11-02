import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { Role } from '../auth/auth.models';
import { AuthStore } from '../auth/auth.store';
import { homeFor } from '../auth/home-route';

/**
 * Espace réservé à un rôle. Avec `canMatch`, le code d'un espace interdit n'est même pas téléchargé ;
 * l'utilisateur est renvoyé vers son propre tableau de bord (US-10). L'API reste la vraie barrière.
 */
export function roleGuard(...allowed: Role[]): CanMatchFn {
  return () => {
    const role = inject(AuthStore).role();
    if (role !== null && allowed.includes(role)) {
      return true;
    }
    return inject(Router).createUrlTree([homeFor(role)]);
  };
}

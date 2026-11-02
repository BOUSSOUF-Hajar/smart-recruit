import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthStore } from '../auth/auth.store';

/** Tant que le mot de passe temporaire n'est pas changé, seul l'écran de changement est accessible (US-11). */
export const passwordChangeGuard: CanMatchFn = () =>
  inject(AuthStore).mustChangePassword()
    ? inject(Router).createUrlTree(['/account/password'])
    : true;

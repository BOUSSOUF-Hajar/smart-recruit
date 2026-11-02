import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthStore } from '../auth/auth.store';
import { homeFor } from '../auth/home-route';

/** Connexion et inscription : un utilisateur déjà connecté va directement à son tableau de bord. */
export const guestGuard: CanMatchFn = () => {
  const store = inject(AuthStore);
  return store.isAuthenticated() ? inject(Router).createUrlTree([homeFor(store.role())]) : true;
};

import { Routes } from '@angular/router';

/** Compte de l'utilisateur connecté, quel que soit son rôle. */
export const accountRoutes: Routes = [
  {
    path: 'password',
    title: $localize`Mot de passe · SmartRecruit`,
    loadComponent: () => import('./pages/change-password.page').then((m) => m.ChangePasswordPage),
  },
];

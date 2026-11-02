import { Routes } from '@angular/router';
import { guestGuard } from '../../core/guards/guest.guard';

/** Routes de l'espace public, chargées à la demande. */
export const publicRoutes: Routes = [
  {
    path: 'jobs',
    title: $localize`Offres d'emploi · SmartRecruit`,
    loadComponent: () => import('./jobs/pages/job-search.page').then((m) => m.JobSearchPage),
  },
  {
    path: 'jobs/:id',
    title: $localize`Offre d'emploi · SmartRecruit`,
    loadComponent: () => import('./jobs/pages/job-detail.page').then((m) => m.JobDetailPage),
  },
  {
    path: 'login',
    title: $localize`Connexion · SmartRecruit`,
    canMatch: [guestGuard],
    loadComponent: () => import('./auth/pages/login.page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    title: $localize`Inscription · SmartRecruit`,
    canMatch: [guestGuard],
    loadComponent: () => import('./auth/pages/register.page').then((m) => m.RegisterPage),
  },
  {
    path: 'forbidden',
    title: $localize`Accès refusé · SmartRecruit`,
    loadComponent: () => import('./errors/pages/forbidden.page').then((m) => m.ForbiddenPage),
  },
  {
    path: 'not-found',
    title: $localize`Page introuvable · SmartRecruit`,
    loadComponent: () => import('./errors/pages/not-found.page').then((m) => m.NotFoundPage),
  },
];

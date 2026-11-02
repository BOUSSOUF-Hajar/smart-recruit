import { Routes } from '@angular/router';
import { PublicLayout } from './layouts/public-layout/public-layout';
import { publicRoutes } from './features/public/public.routes';
import { authGuard } from './core/guards/auth.guard';
import { passwordChangeGuard } from './core/guards/password-change.guard';
import { roleGuard } from './core/guards/role.guard';

/**
 * Routes de premier niveau (architecture frontend §3). Chaque espace connecté est chargé à la
 * demande derrière des guards `canMatch`, dans cet ordre : connecté, mot de passe définitif, rôle.
 * Les routes publiques passent en premier : sans cela, la racine `/` serait retenue par le gabarit
 * des espaces connectés (route vide avec enfants) au lieu d'être redirigée vers `/jobs`.
 */
export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [{ path: '', pathMatch: 'full', redirectTo: 'jobs' }, ...publicRoutes],
  },
  {
    path: '',
    // Gabarit chargé à la demande : un visiteur anonyme ne télécharge ni la barre latérale ni les menus
    loadComponent: () => import('./layouts/app-layout/app-layout').then((m) => m.AppLayout),
    children: [
      {
        path: 'account',
        canMatch: [authGuard],
        loadChildren: () =>
          import('./features/account/account.routes').then((m) => m.accountRoutes),
      },
      {
        path: 'candidate',
        canMatch: [authGuard, passwordChangeGuard, roleGuard('CANDIDATE')],
        loadChildren: () =>
          import('./features/candidate/candidate.routes').then((m) => m.candidateRoutes),
      },
      {
        path: 'recruiter',
        canMatch: [authGuard, passwordChangeGuard, roleGuard('RECRUITER')],
        loadChildren: () =>
          import('./features/recruiter/recruiter.routes').then((m) => m.recruiterRoutes),
      },
      {
        path: 'admin',
        canMatch: [authGuard, passwordChangeGuard, roleGuard('ADMIN')],
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
    ],
  },
  { path: '**', redirectTo: 'not-found' },
];

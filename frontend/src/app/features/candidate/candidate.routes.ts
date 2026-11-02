import { Routes } from '@angular/router';

/** Routes de l'espace candidat, chargées seulement si le guard de rôle laisse passer. */
export const candidateRoutes: Routes = [
  {
    path: '',
    title: $localize`Espace candidat · SmartRecruit`,
    loadComponent: () =>
      import('./pages/candidate-dashboard.page').then((m) => m.CandidateDashboardPage),
  },
  {
    path: 'profile',
    title: $localize`Mon profil · SmartRecruit`,
    loadComponent: () => import('./profile/pages/profile.page').then((m) => m.ProfilePage),
  },
  {
    path: 'applications',
    title: $localize`Mes candidatures · SmartRecruit`,
    loadComponent: () =>
      import('./applications/pages/my-applications.page').then((m) => m.MyApplicationsPage),
  },
  {
    path: 'applications/:id',
    title: $localize`Ma candidature · SmartRecruit`,
    loadComponent: () =>
      import('./applications/pages/application-detail.page').then((m) => m.ApplicationDetailPage),
  },
  {
    path: 'interviews',
    title: $localize`Mes entretiens · SmartRecruit`,
    loadComponent: () => import('./interviews/my-interviews.page').then((m) => m.MyInterviewsPage),
  },
];

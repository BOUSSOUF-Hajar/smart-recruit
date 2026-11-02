import { Routes } from '@angular/router';

/** Routes de l'espace recruteur, chargées seulement si le guard de rôle laisse passer. */
export const recruiterRoutes: Routes = [
  {
    path: '',
    title: $localize`Espace recruteur · SmartRecruit`,
    loadComponent: () =>
      import('./pages/recruiter-dashboard.page').then((m) => m.RecruiterDashboardPage),
  },
  {
    path: 'jobs',
    title: $localize`Offres de l'entreprise · SmartRecruit`,
    loadComponent: () => import('./jobs/pages/job-list.page').then((m) => m.JobListPage),
  },
  {
    path: 'jobs/new',
    title: $localize`Nouvelle offre · SmartRecruit`,
    loadComponent: () => import('./jobs/pages/job-edit.page').then((m) => m.JobEditPage),
  },
  {
    path: 'jobs/:id',
    title: $localize`Offre · SmartRecruit`,
    loadComponent: () => import('./jobs/pages/job-edit.page').then((m) => m.JobEditPage),
  },
  {
    path: 'applications',
    title: $localize`Candidatures · SmartRecruit`,
    loadComponent: () =>
      import('./applications/pages/application-list.page').then((m) => m.ApplicationListPage),
  },
  {
    path: 'applications/:id',
    title: $localize`Dossier de candidature · SmartRecruit`,
    loadComponent: () =>
      import('./applications/pages/application-dossier.page').then((m) => m.ApplicationDossierPage),
  },
  {
    path: 'interviews',
    title: $localize`Entretiens · SmartRecruit`,
    loadComponent: () => import('./interviews/pages/agenda.page').then((m) => m.AgendaPage),
  },
];

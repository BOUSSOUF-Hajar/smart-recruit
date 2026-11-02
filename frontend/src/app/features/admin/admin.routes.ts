import { Routes } from '@angular/router';

/** Routes de l'espace, chargées seulement si le guard de rôle laisse passer. */
export const adminRoutes: Routes = [
  {
    path: '',
    title: $localize`Administration · SmartRecruit`,
    loadComponent: () => import('./pages/admin-dashboard.page').then((m) => m.AdminDashboardPage),
  },
  {
    path: 'users',
    title: $localize`Utilisateurs · SmartRecruit`,
    loadComponent: () => import('./users/user-list.page').then((m) => m.UserListPage),
  },
  {
    path: 'users/new',
    title: $localize`Créer un compte · SmartRecruit`,
    loadComponent: () => import('./users/create-user.page').then((m) => m.CreateUserPage),
  },
  {
    path: 'companies',
    title: $localize`Entreprises · SmartRecruit`,
    loadComponent: () => import('./companies/company-list.page').then((m) => m.CompanyListPage),
  },
  {
    path: 'categories',
    title: $localize`Catégories · SmartRecruit`,
    loadComponent: () => import('./categories/category-list.page').then((m) => m.CategoryListPage),
  },
  {
    path: 'skills',
    title: $localize`Compétences · SmartRecruit`,
    loadComponent: () => import('./skills/skill-list.page').then((m) => m.SkillListPage),
  },
  {
    path: 'audit',
    title: $localize`Journal d'audit · SmartRecruit`,
    loadComponent: () => import('./audit/audit-log.page').then((m) => m.AuditLogPage),
  },
];

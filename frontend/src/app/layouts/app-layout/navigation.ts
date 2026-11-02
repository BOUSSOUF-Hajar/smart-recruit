import { Role } from '../../core/auth/auth.models';

export interface NavItem {
  label: string;
  icon: string;
  link: string;
}

/**
 * Entrées de la barre latérale par rôle. Chaque phase ajoute les siennes (profil, offres,
 * candidatures…) ; masquer une entrée n'est qu'un confort, l'API refuse de toute façon l'accès.
 */
export function navigationFor(role: Role | null): NavItem[] {
  switch (role) {
    case 'CANDIDATE':
      return [
        { label: $localize`Tableau de bord`, icon: 'dashboard', link: '/candidate' },
        { label: $localize`Mon profil`, icon: 'badge', link: '/candidate/profile' },
        { label: $localize`Mes candidatures`, icon: 'send', link: '/candidate/applications' },
        { label: $localize`Mes entretiens`, icon: 'event', link: '/candidate/interviews' },
        { label: $localize`Offres d'emploi`, icon: 'work', link: '/jobs' },
      ];
    case 'RECRUITER':
      return [
        { label: $localize`Tableau de bord`, icon: 'dashboard', link: '/recruiter' },
        { label: $localize`Mes offres`, icon: 'work', link: '/recruiter/jobs' },
        { label: $localize`Candidatures`, icon: 'groups', link: '/recruiter/applications' },
        { label: $localize`Entretiens`, icon: 'event', link: '/recruiter/interviews' },
      ];
    case 'ADMIN':
      return [
        { label: $localize`Tableau de bord`, icon: 'dashboard', link: '/admin' },
        { label: $localize`Utilisateurs`, icon: 'group', link: '/admin/users' },
        { label: $localize`Entreprises`, icon: 'apartment', link: '/admin/companies' },
        { label: $localize`Catégories`, icon: 'category', link: '/admin/categories' },
        { label: $localize`Compétences`, icon: 'psychology', link: '/admin/skills' },
        { label: $localize`Journal d'audit`, icon: 'history', link: '/admin/audit' },
      ];
    default:
      return [];
  }
}

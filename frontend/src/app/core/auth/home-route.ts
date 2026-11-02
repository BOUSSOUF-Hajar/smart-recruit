import { Role } from './auth.models';

/** Tableau de bord de chaque rôle : destination après connexion, et repli des guards. */
export function homeFor(role: Role | null): string {
  switch (role) {
    case 'CANDIDATE':
      return '/candidate';
    case 'RECRUITER':
      return '/recruiter';
    case 'ADMIN':
      return '/admin';
    default:
      return '/jobs';
  }
}

/**
 * N'accepte qu'un chemin interne (`/candidate/...`) comme destination après connexion.
 * Un `returnUrl` comme `https://site-pirate.example` ou `//site-pirate.example` est ignoré :
 * sinon le lien de connexion servirait de redirection ouverte, utile pour l'hameçonnage.
 */
export function safeReturnUrl(value: string | null | undefined): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return null;
  }
  return value;
}

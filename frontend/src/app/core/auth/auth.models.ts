/** Rôles de l'API, identiques aux valeurs du backend. */
export type Role = 'CANDIDATE' | 'RECRUITER' | 'ADMIN';

/** Utilisateur connecté, tel que renvoyé par `/me` et dans les réponses d'authentification. */
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  /** Entreprise du recruteur ; `null` pour les autres rôles. */
  companyId: string | null;
  /** Compte créé par un administrateur, mot de passe temporaire pas encore changé. */
  mustChangePassword: boolean;
}

/** Réponse de register, login et refresh. Le refresh token voyage dans un cookie HttpOnly, jamais ici. */
export interface AuthResponse {
  accessToken: string;
  /** Durée de validité de l'access token, en secondes. */
  expiresIn: number;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  privacyAccepted: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

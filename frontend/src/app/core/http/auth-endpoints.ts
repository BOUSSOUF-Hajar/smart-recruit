/**
 * Endpoints d'authentification qui n'utilisent pas l'access token : ils reçoivent des identifiants
 * ou s'appuient sur le cookie. `/auth/password`, lui, exige le token et n'est pas dans la liste.
 */
const SESSION_ENDPOINTS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/refresh',
  '/api/v1/auth/logout',
];

export function isSessionEndpoint(url: string): boolean {
  const path = url.split('?')[0];
  return SESSION_ENDPOINTS.includes(path);
}

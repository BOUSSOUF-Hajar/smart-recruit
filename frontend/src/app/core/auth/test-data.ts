import { AuthResponse, Role, User } from './auth.models';

/** Données de test partagées par les specs de la session. */
export function aUser(overrides: Partial<User> = {}): User {
  return {
    id: '0192a7c4-5b1e-7c3d-9f20-6a1b2c3d4e5f',
    email: 'salma.idrissi@example.com',
    firstName: 'Salma',
    lastName: 'Idrissi',
    role: 'CANDIDATE',
    companyId: null,
    mustChangePassword: false,
    ...overrides,
  };
}

export function anAuthResponse(token = 'token-1', role: Role = 'CANDIDATE'): AuthResponse {
  return { accessToken: token, expiresIn: 900, user: aUser({ role }) };
}

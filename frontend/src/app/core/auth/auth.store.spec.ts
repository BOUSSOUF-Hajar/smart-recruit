import { TestBed } from '@angular/core/testing';
import { AuthStore } from './auth.store';
import { aUser, anAuthResponse } from './test-data';

describe('AuthStore', () => {
  let store: AuthStore;

  beforeEach(() => {
    store = TestBed.inject(AuthStore);
  });

  it('est anonyme au départ', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
    expect(store.role()).toBeNull();
  });

  it("garde le token et l'utilisateur d'une réponse d'authentification", () => {
    store.start(anAuthResponse('abc', 'RECRUITER'));

    expect(store.isAuthenticated()).toBe(true);
    expect(store.accessToken()).toBe('abc');
    expect(store.role()).toBe('RECRUITER');
    expect(store.displayName()).toBe('Salma Idrissi');
  });

  it('expose le marqueur de mot de passe temporaire', () => {
    store.start({ accessToken: 't', expiresIn: 900, user: aUser({ mustChangePassword: true }) });

    expect(store.mustChangePassword()).toBe(true);
  });

  it('oublie tout à la fermeture de session', () => {
    store.start(anAuthResponse());
    store.clear();

    expect(store.isAuthenticated()).toBe(false);
    expect(store.user()).toBeNull();
  });
});

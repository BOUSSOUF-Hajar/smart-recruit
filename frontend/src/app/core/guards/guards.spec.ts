import { TestBed } from '@angular/core/testing';
import {
  CanMatchFn,
  PartialMatchRouteSnapshot,
  Route,
  Router,
  UrlSegment,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard } from './auth.guard';
import { guestGuard } from './guest.guard';
import { passwordChangeGuard } from './password-change.guard';
import { roleGuard } from './role.guard';
import { AuthStore } from '../auth/auth.store';
import { aUser } from '../auth/test-data';
import { Role } from '../auth/auth.models';

describe('Guards de session', () => {
  let store: AuthStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    store = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
  });

  function run(
    guard: CanMatchFn,
    path = 'candidate/applications',
    queryParams: Record<string, string> = {},
  ): boolean | string {
    const segments = path.split('/').map((part) => new UrlSegment(part, {}));
    const snapshot = { queryParams } as PartialMatchRouteSnapshot;
    const result = TestBed.runInInjectionContext(() => guard({} as Route, segments, snapshot));
    return result instanceof UrlTree ? router.serializeUrl(result) : (result as boolean);
  }

  function signIn(role: Role, mustChangePassword = false): void {
    store.start({ accessToken: 't', expiresIn: 900, user: aUser({ role, mustChangePassword }) });
  }

  describe('authGuard', () => {
    it('envoie un visiteur à la connexion, avec la page demandée', () => {
      expect(run(authGuard)).toBe('/login?returnUrl=%2Fcandidate%2Fapplications');
    });

    it('garde les paramètres de requête de la page demandée', () => {
      expect(run(authGuard, 'recruiter/applications', { status: 'NEW' })).toBe(
        '/login?returnUrl=%2Frecruiter%2Fapplications%3Fstatus%3DNEW',
      );
    });

    it('laisse passer un utilisateur connecté', () => {
      signIn('CANDIDATE');
      expect(run(authGuard)).toBe(true);
    });
  });

  describe('guestGuard', () => {
    it('laisse un visiteur accéder à la connexion', () => {
      expect(run(guestGuard)).toBe(true);
    });

    it('envoie un utilisateur connecté vers son tableau de bord', () => {
      signIn('RECRUITER');
      expect(run(guestGuard)).toBe('/recruiter');
    });
  });

  describe('roleGuard', () => {
    it('laisse passer le bon rôle', () => {
      signIn('ADMIN');
      expect(run(roleGuard('ADMIN'))).toBe(true);
    });

    it('renvoie un autre rôle vers son propre tableau de bord (US-10)', () => {
      signIn('CANDIDATE');
      expect(run(roleGuard('RECRUITER'))).toBe('/candidate');
    });

    it('accepte plusieurs rôles', () => {
      signIn('RECRUITER');
      expect(run(roleGuard('CANDIDATE', 'RECRUITER'))).toBe(true);
    });
  });

  describe('passwordChangeGuard', () => {
    it('bloque tant que le mot de passe temporaire est en place (US-11)', () => {
      signIn('ADMIN', true);
      expect(run(passwordChangeGuard)).toBe('/account/password');
    });

    it('laisse passer ensuite', () => {
      signIn('ADMIN');
      expect(run(passwordChangeGuard)).toBe(true);
    });
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthService } from './auth.service';
import { AuthStore } from './auth.store';
import { NotificationService } from '../errors/notification.service';
import { aUser, anAuthResponse } from './test-data';

describe('AuthService', () => {
  let service: AuthService;
  let store: AuthStore;
  let backend: HttpTestingController;
  const router = { url: '/jobs', navigate: vi.fn(), navigateByUrl: vi.fn() };

  beforeEach(() => {
    router.navigateByUrl.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
        { provide: NotificationService, useValue: { error: vi.fn(), success: vi.fn() } },
      ],
    });
    service = TestBed.inject(AuthService);
    store = TestBed.inject(AuthStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('restaure la session au démarrage si le cookie est valide', async () => {
    const restored = service.restoreSession();
    backend.expectOne('/api/v1/auth/refresh').flush(anAuthResponse('restaure'));
    await restored;

    expect(store.accessToken()).toBe('restaure');
  });

  it('reste anonyme, sans erreur, sans cookie valide', async () => {
    const restored = service.restoreSession();
    backend
      .expectOne('/api/v1/auth/refresh')
      .flush({ code: 'AUTH_REFRESH_INVALID' }, { status: 401, statusText: 'Unauthorized' });

    await expect(restored).resolves.toBeUndefined();
    expect(store.isAuthenticated()).toBe(false);
  });

  it('ouvre la session à la connexion', async () => {
    const login = firstValueFrom(service.login({ email: 'salma@example.com', password: 'x' }));
    backend.expectOne('/api/v1/auth/login').flush(anAuthResponse('t', 'RECRUITER'));
    await login;

    expect(store.role()).toBe('RECRUITER');
  });

  it('obtient un token sans marqueur après le changement de mot de passe', async () => {
    store.start({ accessToken: 'temp', expiresIn: 900, user: aUser({ mustChangePassword: true }) });

    const change = firstValueFrom(
      service.changePassword({ currentPassword: 'a', newPassword: 'nouvelle-phrase-2026' }),
    );
    backend
      .expectOne('/api/v1/auth/password')
      .flush(null, { status: 204, statusText: 'No Content' });
    backend.expectOne('/api/v1/auth/refresh').flush(anAuthResponse('definitif'));
    await change;

    expect(store.mustChangePassword()).toBe(false);
    expect(store.accessToken()).toBe('definitif');
  });

  it('ferme la session locale même si le serveur est injoignable', async () => {
    store.start(anAuthResponse());

    const logout = firstValueFrom(service.logout());
    backend.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    await logout;

    expect(store.isAuthenticated()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});

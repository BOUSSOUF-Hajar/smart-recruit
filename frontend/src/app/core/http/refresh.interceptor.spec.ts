import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { correlationInterceptor } from './correlation.interceptor';
import { errorInterceptor } from './error.interceptor';
import { refreshInterceptor } from './refresh.interceptor';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from '../auth/auth.store';
import { NotificationService } from '../errors/notification.service';
import { ApiError } from '../errors/api-error';
import { anAuthResponse } from '../auth/test-data';

/**
 * La chaîne complète, dans l'ordre de app.config.ts : c'est l'ordre qui est testé autant que
 * chaque intercepteur (architecture frontend §4).
 */
describe('refreshInterceptor dans la chaîne complète', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let store: AuthStore;
  const notifications = { error: vi.fn(), success: vi.fn() };
  const router = { url: '/candidate/applications', navigate: vi.fn(), navigateByUrl: vi.fn() };

  const expired = { status: 401, code: 'AUTH_TOKEN_EXPIRED', title: 'Session expirée', errors: [] };

  beforeEach(() => {
    notifications.error.mockReset();
    router.navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withInterceptors([
            correlationInterceptor,
            errorInterceptor,
            refreshInterceptor,
            authInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notifications },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
    store.start(anAuthResponse('ancien-token'));
  });

  afterEach(() => backend.verify());

  /** Le renouvellement passe par une promesse (verrou Web Locks) : on laisse les microtâches s'exécuter. */
  const settle = () => new Promise<void>((resolve) => setTimeout(resolve));

  it('renouvelle le token expiré puis rejoue la requête, sans notification', async () => {
    const result = firstValueFrom(http.get<{ ok: boolean }>('/api/v1/me'));

    const first = backend.expectOne('/api/v1/me');
    expect(first.request.headers.get('Authorization')).toBe('Bearer ancien-token');
    first.flush(expired, { status: 401, statusText: 'Unauthorized' });

    backend.expectOne('/api/v1/auth/refresh').flush(anAuthResponse('nouveau-token'));
    await settle();

    const replay = backend.expectOne('/api/v1/me');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer nouveau-token');
    replay.flush({ ok: true });

    await expect(result).resolves.toEqual({ ok: true });
    expect(store.accessToken()).toBe('nouveau-token');
    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('ne renouvelle qu’une fois pour plusieurs requêtes expirées en même temps', async () => {
    const results = Promise.all([
      firstValueFrom(http.get('/api/v1/a')),
      firstValueFrom(http.get('/api/v1/b')),
    ]);

    backend.expectOne('/api/v1/a').flush(expired, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/b').flush(expired, { status: 401, statusText: 'Unauthorized' });
    // expectOne échouerait s'il y avait deux appels de renouvellement
    backend.expectOne('/api/v1/auth/refresh').flush(anAuthResponse('nouveau-token'));
    await settle();
    backend.expectOne('/api/v1/a').flush('A');
    backend.expectOne('/api/v1/b').flush('B');

    await expect(results).resolves.toEqual(['A', 'B']);
  });

  it('ferme la session et renvoie à la connexion si le renouvellement échoue', async () => {
    const result = firstValueFrom(http.get('/api/v1/me')).catch((error: ApiError) => error);

    backend.expectOne('/api/v1/me').flush(expired, { status: 401, statusText: 'Unauthorized' });
    backend
      .expectOne('/api/v1/auth/refresh')
      .flush(
        { status: 401, code: 'AUTH_REFRESH_INVALID', errors: [] },
        { status: 401, statusText: 'Unauthorized' },
      );

    const error = await result;
    expect((error as ApiError).code).toBe('AUTH_TOKEN_EXPIRED');
    expect(store.isAuthenticated()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/candidate/applications' },
    });
    // Un seul message : « session expirée », pas d'erreur générique en plus
    expect(notifications.error).toHaveBeenCalledTimes(1);
  });

  it('ferme la session sur un autre 401 (token refusé)', async () => {
    const result = firstValueFrom(http.get('/api/v1/me')).catch((error: ApiError) => error);

    backend
      .expectOne('/api/v1/me')
      .flush(
        { status: 401, code: 'AUTHENTICATION_REQUIRED', errors: [] },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(((await result) as ApiError).code).toBe('AUTHENTICATION_REQUIRED');
    expect(store.isAuthenticated()).toBe(false);
    backend.expectNone('/api/v1/auth/refresh');
  });

  it('laisse passer les erreurs des endpoints de session (identifiants refusés)', async () => {
    store.clear();
    const result = firstValueFrom(http.post('/api/v1/auth/login', {})).catch((e: ApiError) => e);

    backend
      .expectOne('/api/v1/auth/login')
      .flush(
        { status: 401, code: 'AUTH_INVALID_CREDENTIALS', errors: [] },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(((await result) as ApiError).code).toBe('AUTH_INVALID_CREDENTIALS');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});

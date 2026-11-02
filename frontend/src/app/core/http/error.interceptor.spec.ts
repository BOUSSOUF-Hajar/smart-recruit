import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { errorInterceptor } from './error.interceptor';
import { NotificationService } from '../errors/notification.service';
import { ApiError, NETWORK_ERROR } from '../errors/api-error';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  const notifications = { error: vi.fn(), success: vi.fn() };

  beforeEach(() => {
    notifications.error.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notifications },
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  /** Lance une requête, fait répondre le faux serveur, et renvoie l'erreur reçue par l'appelant. */
  async function failWith(
    status: number,
    body: string | object,
    statusText = 'Error',
  ): Promise<ApiError> {
    const result = firstValueFrom(http.get('/api/v1/test'));
    backend.expectOne('/api/v1/test').flush(body, { status, statusText });
    return result.then(
      () => {
        throw new Error('la requête aurait dû échouer');
      },
      (error: ApiError) => error,
    );
  }

  it('transforme une erreur Problem Details en ApiError typée', async () => {
    const error = await failWith(422, {
      status: 422,
      code: 'PROFILE_INCOMPLETE',
      title: 'Profil incomplet',
      correlationId: 'abc123def456',
      errors: [],
      missing: ['headline', 'resume'],
    });

    expect(error.code).toBe('PROFILE_INCOMPLETE');
    expect(error.extensions['missing']).toEqual(['headline', 'resume']);
    // Erreur métier : c'est l'écran qui l'affiche, pas de notification globale
    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('affiche une notification générique pour une erreur serveur, avec la référence', async () => {
    const error = await failWith(500, {
      status: 500,
      code: 'INTERNAL_ERROR',
      correlationId: 'ref-0042abcd',
    });

    expect(error.code).toBe('INTERNAL_ERROR');
    expect(notifications.error).toHaveBeenCalledTimes(1);
    expect(notifications.error.mock.calls[0][0]).toContain('ref-0042abcd');
  });

  it('notifie quand le serveur est injoignable', async () => {
    const result = firstValueFrom(http.get('/api/v1/test'));
    backend.expectOne('/api/v1/test').error(new ProgressEvent('error'), { status: 0 });
    const error = await result.catch((e: ApiError) => e);

    expect((error as ApiError).code).toBe(NETWORK_ERROR);
    expect(notifications.error).toHaveBeenCalledTimes(1);
  });

  it('notifie un 429 et un 403', async () => {
    await failWith(429, { status: 429, code: 'RATE_LIMITED' });
    await failWith(403, { status: 403, code: 'ACCESS_DENIED' });

    expect(notifications.error).toHaveBeenCalledTimes(2);
  });

  it('laisse les erreurs de validation et les 404 à l’écran', async () => {
    const validation = await failWith(400, {
      status: 400,
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'email', message: 'doit être une adresse valide' }],
    });
    await failWith(404, { status: 404, code: 'RESOURCE_NOT_FOUND' });

    expect(validation.errors).toEqual([
      { field: 'email', message: 'doit être une adresse valide' },
    ]);
    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('supporte une réponse d’erreur qui n’est pas au format attendu', async () => {
    const error = await failWith(502, '<html>Bad Gateway</html>', 'Bad Gateway');

    expect(error.code).toBe('INTERNAL_ERROR');
    expect(error.errors).toEqual([]);
    expect(notifications.error).toHaveBeenCalledTimes(1);
  });
});

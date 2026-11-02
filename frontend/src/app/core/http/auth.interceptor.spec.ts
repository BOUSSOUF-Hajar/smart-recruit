import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthStore } from '../auth/auth.store';
import { anAuthResponse } from '../auth/test-data';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let store: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => backend.verify());

  function authorizationOf(url: string, method: 'GET' | 'POST' = 'GET'): string | null {
    (method === 'GET' ? http.get(url) : http.post(url, {})).subscribe();
    const request = backend.expectOne(url);
    request.flush({});
    return request.request.headers.get('Authorization');
  }

  it('ajoute le token aux appels de notre API', () => {
    store.start(anAuthResponse('abc'));

    expect(authorizationOf('/api/v1/me')).toBe('Bearer abc');
    expect(authorizationOf('/api/v1/auth/password', 'POST')).toBe('Bearer abc');
  });

  it("n'envoie rien quand personne n'est connecté", () => {
    expect(authorizationOf('/api/v1/jobs')).toBeNull();
  });

  it("n'envoie jamais le token à un autre domaine", () => {
    store.start(anAuthResponse('abc'));

    expect(authorizationOf('https://cdn.example.com/data.json')).toBeNull();
  });

  it.each([
    '/api/v1/auth/login',
    '/api/v1/auth/register',
    '/api/v1/auth/refresh',
    '/api/v1/auth/logout',
  ])("n'ajoute pas le token à %s", (url) => {
    store.start(anAuthResponse('abc'));

    expect(authorizationOf(url, 'POST')).toBeNull();
  });
});

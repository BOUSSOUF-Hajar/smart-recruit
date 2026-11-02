import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CORRELATION_HEADER, correlationInterceptor } from './correlation.interceptor';

describe('correlationInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('ajoute un identifiant de corrélation aux appels de notre API', () => {
    http.get('/api/v1/jobs').subscribe();
    const request = backend.expectOne('/api/v1/jobs').request;

    expect(request.headers.get(CORRELATION_HEADER)).toMatch(/^[A-Za-z0-9-]{8,64}$/);
  });

  it('génère un identifiant différent à chaque requête', () => {
    http.get('/api/v1/a').subscribe();
    http.get('/api/v1/b').subscribe();

    const first = backend.expectOne('/api/v1/a').request.headers.get(CORRELATION_HEADER);
    const second = backend.expectOne('/api/v1/b').request.headers.get(CORRELATION_HEADER);
    expect(first).not.toEqual(second);
  });

  it("n'envoie rien à un domaine tiers", () => {
    http.get('https://fonts.googleapis.com/css').subscribe();

    expect(
      backend.expectOne('https://fonts.googleapis.com/css').request.headers.has(CORRELATION_HEADER),
    ).toBe(false);
  });
});

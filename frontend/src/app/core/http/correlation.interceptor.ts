import { HttpInterceptorFn } from '@angular/common/http';

export const CORRELATION_HEADER = 'X-Correlation-Id';

/**
 * Ajoute un identifiant de corrélation à chaque appel de notre API.
 * Le même identifiant apparaît dans les logs du backend et dans les erreurs affichées :
 * un utilisateur peut le communiquer au support.
 */
export const correlationInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isOwnApi(request.url) || request.headers.has(CORRELATION_HEADER)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { [CORRELATION_HEADER]: crypto.randomUUID() } }));
};

/** Seuls les appels relatifs vers /api sont concernés : jamais un domaine tiers. */
export function isOwnApi(url: string): boolean {
  return url.startsWith('/api/');
}

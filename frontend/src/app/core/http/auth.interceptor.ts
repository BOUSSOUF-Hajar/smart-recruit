import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from '../auth/auth.store';
import { isOwnApi } from './correlation.interceptor';
import { isSessionEndpoint } from './auth-endpoints';

/**
 * Ajoute `Authorization: Bearer <token>` aux appels de notre API, et seulement à eux :
 * le token ne doit jamais partir vers un autre domaine.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthStore).accessToken();
  if (!token || !isOwnApi(request.url) || isSessionEndpoint(request.url)) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

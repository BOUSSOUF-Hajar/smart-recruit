import {
  ApplicationConfig,
  ErrorHandler,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { routes } from './app.routes';
import { correlationInterceptor } from './core/http/correlation.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { refreshInterceptor } from './core/http/refresh.interceptor';
import { authInterceptor } from './core/http/auth.interceptor';
import { provideSessionRestore } from './core/auth/session-initializer';
import { ReferentialApi } from './core/referential/referential.api';
import { SKILL_SEARCH } from './shared/ui/skill-autocomplete/skill-search';

import { MAT_ICON_DEFAULT_OPTIONS } from '@angular/material/icon';
import { GlobalErrorHandler } from './core/errors/global-error-handler';

// Dates et nombres au format français (« 12 mars 2026 », « 1,4 Mo »)
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    { provide: LOCALE_ID, useValue: 'fr' },
    // Le composant partagé d'autocomplétion reçoit l'accès au référentiel sans dépendre de core
    { provide: SKILL_SEARCH, useExisting: ReferentialApi },
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
    // Icônes Material Symbols, hébergées avec l'application (aucune requête vers Google : CSP 'self')
    { provide: MAT_ICON_DEFAULT_OPTIONS, useValue: { fontSet: 'material-symbols-outlined' } },
    provideRouter(
      routes,
      withComponentInputBinding(), // paramètres de route et de requête → input() des pages
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
    ),
    // Ordre des intercepteurs (architecture frontend §4) : la requête les traverse de gauche à droite,
    // la réponse de droite à gauche. « error » ne voit donc que l'erreur finale, après un éventuel
    // renouvellement, et la requête rejouée par « refresh » repasse par « auth » avec le nouveau token.
    provideHttpClient(
      withFetch(),
      withInterceptors([
        correlationInterceptor,
        errorInterceptor,
        refreshInterceptor,
        authInterceptor,
      ]),
    ),
    provideSessionRestore(),
  ],
};

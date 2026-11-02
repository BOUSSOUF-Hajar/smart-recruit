import { EnvironmentProviders, inject, provideAppInitializer } from '@angular/core';
import { AuthService } from './auth.service';

/**
 * Restaure la session avant le premier affichage : sans cela, un utilisateur connecté qui
 * recharge la page serait brièvement vu comme anonyme, et les guards le renverraient à la connexion.
 */
export function provideSessionRestore(): EnvironmentProviders {
  return provideAppInitializer(() => inject(AuthService).restoreSession());
}

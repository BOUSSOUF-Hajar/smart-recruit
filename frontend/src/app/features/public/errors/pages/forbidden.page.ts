import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';

/** Affichée par les guards de rôle (phase 3) ; la vraie protection reste dans l'API. */
@Component({
  selector: 'app-forbidden-page',
  imports: [EmptyState, RouterLink, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-empty-state
      icon="lock"
      title="Accès refusé"
      i18n-title
      message="Vous n'avez pas accès à cette page."
      i18n-message
    >
      <a mat-flat-button routerLink="/" i18n>Retour à l'accueil</a>
    </app-empty-state>
  `,
})
export class ForbiddenPage {}

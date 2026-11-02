import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';

@Component({
  selector: 'app-not-found-page',
  imports: [EmptyState, RouterLink, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-empty-state
      icon="search_off"
      title="Page introuvable"
      i18n-title
      message="Cette page n'existe pas, ou elle n'est plus disponible."
      i18n-message
    >
      <a mat-flat-button routerLink="/jobs" i18n>Voir les offres</a>
    </app-empty-state>
  `,
})
export class NotFoundPage {}

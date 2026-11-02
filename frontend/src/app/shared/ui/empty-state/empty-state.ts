import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Message centré avec une icône, pour une liste vide ou une page sans contenu.
 * L'action éventuelle est projetée par le parent : `<app-empty-state ...><a mat-button>…</a></app-empty-state>`.
 */
@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="empty-state">
      <mat-icon class="empty-state__icon" aria-hidden="true">{{ icon() }}</mat-icon>
      <h1 class="empty-state__title">{{ title() }}</h1>
      @if (message()) {
        <p class="empty-state__message">{{ message() }}</p>
      }
      <div class="empty-state__actions"><ng-content /></div>
    </section>
  `,
  styles: `
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 48px 16px;
      text-align: center;
    }
    .empty-state__icon {
      width: 56px;
      height: 56px;
      font-size: 56px;
      color: var(--mat-sys-primary);
    }
    .empty-state__title {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }
    .empty-state__message {
      margin: 0;
      max-width: 48ch;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class EmptyState {
  readonly icon = input('info');
  readonly title = input.required<string>();
  readonly message = input<string>();
}

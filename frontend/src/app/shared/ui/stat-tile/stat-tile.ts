import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';

/**
 * Indicateur chiffré (cahier des charges D8). Avec un lien, le chiffre mène à la liste filtrée
 * correspondante, accessible au clavier ; sans lien (aucune liste à ouvrir), c'est un simple bloc.
 */
@Component({
  selector: 'app-stat-tile',
  imports: [RouterLink, NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (link(); as target) {
      <a class="tile link" [routerLink]="target" [queryParams]="params()">
        <ng-container *ngTemplateOutlet="content" />
      </a>
    } @else {
      <div class="tile">
        <ng-container *ngTemplateOutlet="content" />
      </div>
    }
    <ng-template #content>
      <span class="value">{{ value() }}</span>
      <span class="label">{{ label() }}</span>
      @if (hint()) {
        <span class="hint">{{ hint() }}</span>
      }
    </ng-template>
  `,
  styles: `
    .tile {
      display: flex;
      flex-direction: column;
      gap: 2px;
      height: 100%;
      padding: 16px;
      border-radius: 12px;
      color: inherit;
      text-decoration: none;
      background: var(--mat-sys-surface-container-low);
      box-sizing: border-box;
    }
    .link:hover,
    .link:focus-visible {
      background: var(--mat-sys-surface-container);
    }
    .value {
      font: var(--mat-sys-display-small);
      color: var(--mat-sys-primary);
    }
    .label {
      font: var(--mat-sys-title-small);
    }
    .hint {
      font: var(--mat-sys-body-small);
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class StatTile {
  readonly value = input.required<number>();
  readonly label = input.required<string>();
  readonly hint = input<string>();
  readonly link = input<string | unknown[] | null>(null);
  readonly params = input<Record<string, string | number | null>>({});
}

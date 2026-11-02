import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Completeness } from '../data-access/profile.models';
import { CRITERION_LABELS } from '../data-access/profile-labels';

/** US-20 : pourcentage de complétude et critères manquants, chacun menant à son onglet. */
@Component({
  selector: 'app-completeness-card',
  imports: [MatButtonModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" aria-labelledby="completeness-title">
      <div class="header">
        <h2 id="completeness-title" i18n>Profil complété à {{ completeness().percent }} %</h2>
      </div>
      <mat-progress-bar
        mode="determinate"
        [value]="completeness().percent"
        aria-labelledby="completeness-title"
      />
      @if (missing().length) {
        <p class="hint" i18n>Pour un profil complet, ajoutez :</p>
        <ul class="missing">
          @for (item of missing(); track item.key) {
            <li>
              <button mat-button type="button" (click)="goTo.emit(item.tab)">
                {{ item.label }}
              </button>
            </li>
          }
        </ul>
      } @else {
        <p class="hint" i18n>Votre profil est complet.</p>
      }
    </section>
  `,
  styles: `
    .card {
      padding: 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    h2 {
      margin: 0 0 8px;
      font: var(--mat-sys-title-medium);
    }
    .hint {
      margin: 12px 0 4px;
      color: var(--mat-sys-on-surface-variant);
    }
    .missing {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
  `,
})
export class CompletenessCard {
  readonly completeness = input.required<Completeness>();
  /** Index de l'onglet où compléter le critère choisi. */
  readonly goTo = output<number>();

  protected readonly missing = computed(() =>
    this.completeness().missing.map((key) => ({ key, ...CRITERION_LABELS[key] })),
  );
}

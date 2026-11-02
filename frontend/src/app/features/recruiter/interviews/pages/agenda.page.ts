import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Page } from '../../../../shared/models/job';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { InterviewCard } from '../components/interview-card';
import { InterviewActions } from '../data-access/interview-actions';
import { RecruiterInterview, weekRange } from '../data-access/interviews.models';

/** Jour local (yyyy-MM-dd) d'un instant, pour regrouper l'agenda par journée. */
function dayKey(iso: string): string {
  const date = new Date(iso);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

/**
 * F-INT-05 : agenda de l'entreprise, semaine par semaine, dans le fuseau du navigateur (RG-32).
 * La semaine affichée et le filtre « mes entretiens » vivent dans l'URL.
 */
@Component({
  selector: 'app-agenda-page',
  imports: [
    DatePipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    EmptyState,
    InterviewCard,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <h1 i18n>Entretiens</h1>
      <mat-button-toggle-group
        [value]="mine() === 'true' ? 'mine' : 'all'"
        (change)="setMine($event.value === 'mine')"
        aria-label="Filtre des participants"
        i18n-aria-label
      >
        <mat-button-toggle value="all" i18n>Toute l'entreprise</mat-button-toggle>
        <mat-button-toggle value="mine" i18n>Mes entretiens</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    <nav class="week" aria-label="Semaine affichée" i18n-aria-label>
      <button
        mat-icon-button
        type="button"
        (click)="shift(-7)"
        aria-label="Semaine précédente"
        i18n-aria-label
      >
        <mat-icon>chevron_left</mat-icon>
      </button>
      <span>
        <ng-container i18n>Semaine du {{ range().from | date: 'd MMMM y' }}</ng-container>
      </span>
      <button
        mat-icon-button
        type="button"
        (click)="shift(7)"
        aria-label="Semaine suivante"
        i18n-aria-label
      >
        <mat-icon>chevron_right</mat-icon>
      </button>
      <button mat-button type="button" (click)="today()" i18n>Aujourd'hui</button>
    </nav>

    @if (interviews.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (interviews.error()) {
      <p role="alert" i18n>L'agenda n'a pas pu être chargé.</p>
    } @else {
      @for (day of days(); track day.key) {
        <section>
          <h2>{{ day.date | date: 'EEEE d MMMM' }}</h2>
          @for (interview of day.items; track interview.id) {
            <app-interview-card
              [interview]="interview"
              [showApplication]="true"
              (editRequested)="run(actions.reschedule($event))"
              (cancelRequested)="run(actions.cancel($event))"
              (completeRequested)="run(actions.complete($event))"
            />
          }
        </section>
      } @empty {
        @if (interviews.hasValue()) {
          <app-empty-state
            icon="event_available"
            title="Aucun entretien cette semaine"
            i18n-title
          />
        }
      }
    }
  `,
  styles: `
    .header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }
    h2 {
      margin: 24px 0 8px;
      font: var(--mat-sys-title-medium);
      text-transform: capitalize;
    }
    .week {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px;
      margin: 16px 0;
    }
    app-interview-card {
      display: block;
      margin-bottom: 8px;
    }
  `,
})
export class AgendaPage {
  private readonly router = inject(Router);
  protected readonly actions = inject(InterviewActions);

  /** Paramètres de requête : un jour de la semaine voulue (yyyy-MM-dd) et le filtre « mes entretiens ». */
  readonly week = input<string>();
  readonly mine = input<string>();

  protected readonly range = computed(() => {
    const value = this.week();
    const day = value ? new Date(`${value}T12:00:00`) : new Date();
    return weekRange(Number.isNaN(day.getTime()) ? new Date() : day);
  });

  protected readonly interviews = httpResource<Page<RecruiterInterview>>(() => ({
    url: '/api/v1/interviews',
    params: { ...this.range(), mine: this.mine() === 'true', size: 100 },
  }));

  protected readonly days = computed(() => {
    const content = this.interviews.hasValue() ? this.interviews.value().content : [];
    const groups = new Map<string, RecruiterInterview[]>();
    for (const interview of content) {
      const key = dayKey(interview.startsAt);
      groups.set(key, [...(groups.get(key) ?? []), interview]);
    }
    return [...groups.entries()].map(([key, items]) => ({ key, date: items[0].startsAt, items }));
  });

  protected shift(days: number): void {
    const start = new Date(this.range().from);
    start.setDate(start.getDate() + days);
    this.navigate({ week: dayKey(start.toISOString()) });
  }

  protected today(): void {
    this.navigate({ week: null });
  }

  protected setMine(mine: boolean): void {
    this.navigate({ mine: mine ? 'true' : null });
  }

  protected run(action: ReturnType<InterviewActions['cancel']>): void {
    action.subscribe(() => this.interviews.reload());
  }

  private navigate(queryParams: Record<string, string | null>): void {
    void this.router.navigate([], { queryParams, queryParamsHandling: 'merge' });
  }
}

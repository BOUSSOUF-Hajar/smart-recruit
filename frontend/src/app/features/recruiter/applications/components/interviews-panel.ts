import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Observable } from 'rxjs';
import { ApplicationStatus } from '../../../../shared/models/application';
import { Page } from '../../../../shared/models/job';
import { InterviewCard } from '../../interviews/components/interview-card';
import { InterviewActions } from '../../interviews/data-access/interview-actions';
import { RecruiterInterview } from '../../interviews/data-access/interviews.models';

/**
 * Onglet « Entretiens » du dossier (US-35 à US-37). Planifier n'est proposé qu'au statut
 * INTERVIEW (RG-21) ; clôturer, qu'après l'heure de début.
 */
@Component({
  selector: 'app-interviews-panel',
  imports: [MatButtonModule, MatIconModule, InterviewCard],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      @if (status() === 'INTERVIEW') {
        <button mat-flat-button type="button" (click)="run(actions.schedule(applicationId()))">
          <mat-icon>event</mat-icon>
          <span i18n>Planifier un entretien</span>
        </button>
      } @else {
        <p class="sub" i18n>Les entretiens se planifient au statut « Entretien ».</p>
      }
    </div>
    @for (interview of list(); track interview.id) {
      <app-interview-card
        [interview]="interview"
        (editRequested)="run(actions.reschedule($event))"
        (cancelRequested)="run(actions.cancel($event))"
        (completeRequested)="run(actions.complete($event))"
      />
    } @empty {
      @if (interviews.hasValue()) {
        <p class="sub" i18n>Aucun entretien pour cette candidature.</p>
      }
    }
  `,
  styles: `
    .header {
      margin-bottom: 16px;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    app-interview-card {
      display: block;
      margin-bottom: 8px;
    }
  `,
})
export class InterviewsPanel {
  protected readonly actions = inject(InterviewActions);

  readonly applicationId = input.required<string>();
  readonly status = input.required<ApplicationStatus>();
  /** Un entretien a changé : le dossier recharge ses transitions (RG-33) et son historique. */
  readonly changed = output<void>();

  protected readonly interviews = httpResource<Page<RecruiterInterview>>(() => ({
    url: '/api/v1/interviews',
    params: { applicationId: this.applicationId(), size: 100 },
  }));
  protected readonly list = computed(() =>
    this.interviews.hasValue() ? this.interviews.value().content : [],
  );

  protected run(action: Observable<RecruiterInterview>): void {
    action.subscribe(() => {
      this.interviews.reload();
      this.changed.emit();
    });
  }
}

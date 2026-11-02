import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import {
  FEEDBACK_RATING_LABELS,
  INTERVIEW_STATUS_LABELS,
  INTERVIEW_STATUS_TONES,
  INTERVIEW_TYPE_LABELS,
} from '../../../../shared/models/interview';
import { StatusChip } from '../../../../shared/ui/status-chip/status-chip';
import { RecruiterInterview, hasStarted } from '../data-access/interviews.models';

/** Un entretien vu par un recruteur, avec les actions permises par son état. */
@Component({
  selector: 'app-interview-card',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let i = interview();
    <article class="card">
      <header class="top">
        <div>
          <strong>{{ i.startsAt | date: 'EEEE d MMMM y, HH:mm' }}</strong>
          <span class="sub"> · {{ i.durationMinutes }} min · {{ typeLabel() }}</span>
          @if (showApplication()) {
            <div>
              <a [routerLink]="['/recruiter/applications', i.application.id]">{{
                i.application.candidateName
              }}</a>
              <span class="sub"> · {{ i.application.jobTitle }}</span>
            </div>
          }
        </div>
        <app-status-chip [label]="statusLabel()" [tone]="statusTone()" />
      </header>
      @if (i.meetingUrl) {
        <p>
          <a [href]="i.meetingUrl" target="_blank" rel="noopener noreferrer">{{ i.meetingUrl }}</a>
        </p>
      }
      @if (i.address) {
        <p>{{ i.address }}</p>
      }
      <p class="sub" i18n>Participants : {{ participants() }}</p>
      @if (i.cancellationReason) {
        <p class="sub" i18n>Motif d'annulation : {{ i.cancellationReason }}</p>
      }
      @if (i.feedback; as feedback) {
        <p class="feedback">
          <strong i18n>Avis : {{ ratingLabel() }}</strong>
          @if (feedback.comment) {
            — {{ feedback.comment }}
          }
        </p>
      }
      @if (i.status === 'SCHEDULED') {
        <div class="actions">
          <button mat-button type="button" (click)="editRequested.emit(i)" i18n>Modifier</button>
          <button mat-button type="button" (click)="cancelRequested.emit(i)" i18n>Annuler</button>
          @if (started()) {
            <button mat-flat-button type="button" (click)="completeRequested.emit(i)" i18n>
              Clôturer
            </button>
          }
        </div>
      }
    </article>
  `,
  styles: `
    .card {
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px;
    }
    p {
      margin: 6px 0 0;
      overflow-wrap: anywhere;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .feedback {
      padding: 6px 10px;
      border-radius: 8px;
      background: var(--mat-sys-surface-container-high);
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 8px;
    }
  `,
})
export class InterviewCard {
  readonly interview = input.required<RecruiterInterview>();
  readonly showApplication = input(false);
  readonly editRequested = output<RecruiterInterview>();
  readonly cancelRequested = output<RecruiterInterview>();
  readonly completeRequested = output<RecruiterInterview>();

  protected readonly typeLabel = computed(() => INTERVIEW_TYPE_LABELS[this.interview().type]);
  protected readonly statusLabel = computed(() => INTERVIEW_STATUS_LABELS[this.interview().status]);
  protected readonly statusTone = computed(() => INTERVIEW_STATUS_TONES[this.interview().status]);
  protected readonly participants = computed(() =>
    this.interview()
      .participants.map((p) => p.name)
      .join(', '),
  );
  protected readonly started = computed(() => hasStarted(this.interview()));
  protected readonly ratingLabel = computed(() => {
    const feedback = this.interview().feedback;
    return feedback ? FEEDBACK_RATING_LABELS[feedback.rating] : '';
  });
}

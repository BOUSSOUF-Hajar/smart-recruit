import { ChangeDetectionStrategy, Component, computed } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import {
  INTERVIEW_STATUS_LABELS,
  INTERVIEW_STATUS_TONES,
  INTERVIEW_TYPE_LABELS,
  InterviewStatus,
  InterviewType,
} from '../../../shared/models/interview';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { StatusChip } from '../../../shared/ui/status-chip/status-chip';

/** Entretien vu par le candidat (docs/api.md §5) : de quoi se préparer, rien de l'évaluation. */
export interface CandidateInterview {
  id: string;
  jobTitle: string;
  companyName: string;
  startsAt: string;
  durationMinutes: number;
  type: InterviewType;
  meetingUrl: string | null;
  address: string | null;
  candidateInstructions: string | null;
  status: InterviewStatus;
}

/**
 * US-38 : mes entretiens, les prochains en premier, à l'heure de mon fuseau (RG-32). Un entretien
 * annulé reste affiché, marqué comme tel.
 */
@Component({
  selector: 'app-my-interviews-page',
  imports: [DatePipe, MatIconModule, MatProgressBarModule, EmptyState, StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Mes entretiens</h1>
    @if (upcoming.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <h2 i18n>À venir</h2>
    @for (interview of upcomingList(); track interview.id) {
      <article class="card" [class.cancelled]="interview.status === 'CANCELLED'">
        <header class="top">
          <div>
            <strong>{{ interview.startsAt | date: 'EEEE d MMMM y, HH:mm' }}</strong>
            <span class="sub">
              · {{ interview.durationMinutes }} min · {{ typeLabel(interview.type) }}</span
            >
            <div>{{ interview.jobTitle }} · {{ interview.companyName }}</div>
          </div>
          <app-status-chip
            [label]="statusLabel(interview.status)"
            [tone]="statusTone(interview.status)"
          />
        </header>
        @if (interview.status === 'SCHEDULED') {
          @if (interview.meetingUrl) {
            <p>
              <mat-icon aria-hidden="true">videocam</mat-icon>
              <a [href]="interview.meetingUrl" target="_blank" rel="noopener noreferrer">{{
                interview.meetingUrl
              }}</a>
            </p>
          }
          @if (interview.address) {
            <p><mat-icon aria-hidden="true">place</mat-icon> {{ interview.address }}</p>
          }
          @if (interview.candidateInstructions) {
            <p class="instructions">{{ interview.candidateInstructions }}</p>
          }
        }
      </article>
    } @empty {
      @if (upcoming.hasValue()) {
        <app-empty-state
          icon="event_available"
          title="Aucun entretien à venir"
          i18n-title
          message="Quand un recruteur planifiera un entretien, il apparaîtra ici."
          i18n-message
        />
      }
    }

    @if (pastList().length) {
      <h2 i18n>Passés</h2>
      @for (interview of pastList(); track interview.id) {
        <article class="card past">
          <header class="top">
            <div>
              <strong>{{ interview.startsAt | date: 'd MMMM y, HH:mm' }}</strong>
              <span class="sub"> · {{ typeLabel(interview.type) }}</span>
              <div>{{ interview.jobTitle }} · {{ interview.companyName }}</div>
            </div>
            <app-status-chip
              [label]="statusLabel(interview.status)"
              [tone]="statusTone(interview.status)"
            />
          </header>
        </article>
      }
    }
  `,
  styles: `
    h1 {
      margin: 0 0 16px;
      font: var(--mat-sys-headline-small);
    }
    h2 {
      margin: 24px 0 8px;
      font: var(--mat-sys-title-medium);
    }
    .card {
      margin-bottom: 8px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .cancelled,
    .past {
      opacity: 0.75;
    }
    .top {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px;
    }
    p {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: 8px 0 0;
      overflow-wrap: anywhere;
    }
    .instructions {
      white-space: pre-line;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class MyInterviewsPage {
  protected readonly upcoming = httpResource<CandidateInterview[]>(
    () => '/api/v1/candidates/me/interviews',
  );
  protected readonly past = httpResource<CandidateInterview[]>(() => ({
    url: '/api/v1/candidates/me/interviews',
    params: { upcoming: false },
  }));
  protected readonly upcomingList = computed(() =>
    this.upcoming.hasValue() ? this.upcoming.value() : [],
  );
  protected readonly pastList = computed(() => (this.past.hasValue() ? this.past.value() : []));

  protected typeLabel(type: InterviewType): string {
    return INTERVIEW_TYPE_LABELS[type];
  }

  protected statusLabel(status: InterviewStatus): string {
    return INTERVIEW_STATUS_LABELS[status];
  }

  protected statusTone(status: InterviewStatus) {
    return INTERVIEW_STATUS_TONES[status];
  }
}

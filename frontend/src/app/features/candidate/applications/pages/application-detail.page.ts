import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { CANDIDATE_STATUS_LABELS, CandidateStatus } from '../../../../shared/models/application';
import {
  INTERVIEW_STATUS_LABELS,
  INTERVIEW_TYPE_LABELS,
  InterviewStatus,
  InterviewType,
} from '../../../../shared/models/interview';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { CandidateStatusChip } from '../components/candidate-status';
import { WithdrawDialog, WithdrawDialogResult } from '../components/withdraw-dialog';
import { CandidateApplicationsApi } from '../data-access/candidate-applications.api';
import { CandidateApplication } from '../data-access/candidate-applications.models';

/** US-28, US-29, US-34 : où en est ma candidature, et la retirer si je ne suis plus intéressé. */
@Component({
  selector: 'app-application-detail-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    EmptyState,
    CandidateStatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a mat-button routerLink="/candidate/applications" class="back">
      <mat-icon>arrow_back</mat-icon>
      <span i18n>Mes candidatures</span>
    </a>

    @if (application.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (detail(); as a) {
      <header class="header">
        <div>
          <h1>{{ a.job.title }}</h1>
          <p class="sub" i18n>
            {{ a.job.companyName }} · envoyée le {{ a.submittedAt | date: 'd MMMM y' }}
          </p>
        </div>
        <app-candidate-status [status]="a.candidateStatus" />
      </header>

      <section>
        <h2 i18n>Suivi</h2>
        <ol class="timeline">
          @for (step of a.timeline ?? []; track step.at) {
            <li>
              <span class="dot" aria-hidden="true"></span>
              <span class="label">{{ label(step.candidateStatus) }}</span>
              <time class="sub" [attr.datetime]="step.at">{{
                step.at | date: 'd MMM y, HH:mm'
              }}</time>
            </li>
          }
        </ol>
      </section>

      @if (a.interviews?.length) {
        <section>
          <h2 i18n>Entretiens</h2>
          <ul class="interviews">
            @for (interview of a.interviews; track interview.id) {
              <li>
                {{ interview.startsAt | date: 'EEEE d MMMM y, HH:mm' }} ·
                {{ interviewType(interview.type) }} ·
                {{ interviewStatus(interview.status) }}
              </li>
            }
          </ul>
          <a mat-button routerLink="/candidate/interviews" i18n
            >Détails et lieux dans « Mes entretiens »</a
          >
        </section>
      }

      <div class="actions">
        <a mat-button [routerLink]="['/jobs', a.job.id]" i18n>Voir l'offre</a>
        @if (a.canWithdraw) {
          <button mat-stroked-button type="button" (click)="withdraw(a)" [disabled]="busy()" i18n>
            Retirer ma candidature
          </button>
        }
      </div>
    } @else if (notFound()) {
      <app-empty-state icon="search_off" title="Candidature introuvable" i18n-title>
        <a mat-flat-button routerLink="/candidate/applications" i18n>Mes candidatures</a>
      </app-empty-state>
    } @else if (application.error()) {
      <p role="alert" i18n>La candidature n'a pas pu être chargée.</p>
    }
  `,
  styles: `
    .back {
      margin-bottom: 8px;
    }
    .header {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
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
    }
    .sub {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .timeline {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .timeline li {
      display: grid;
      grid-template-columns: 16px 1fr auto;
      align-items: center;
      gap: 12px;
      padding: 8px 0;
      border-left: 2px solid var(--mat-sys-outline-variant);
      margin-left: 7px;
      padding-left: 0;
    }
    .dot {
      width: 12px;
      height: 12px;
      margin-left: -7px;
      border-radius: 50%;
      background: var(--mat-sys-primary);
    }
    .interviews {
      margin: 0;
      padding-left: 20px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 8px;
      margin-top: 24px;
    }
  `,
})
export class ApplicationDetailPage {
  private readonly api = inject(CandidateApplicationsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  /** Paramètre de route `:id`. */
  readonly id = input.required<string>();

  protected readonly application = httpResource<CandidateApplication>(
    () => `/api/v1/candidates/me/applications/${this.id()}`,
  );
  protected readonly detail = computed(() =>
    this.application.hasValue() ? this.application.value() : undefined,
  );
  protected readonly notFound = computed(() => {
    const error = this.application.error();
    return isApiError(error) && error.status === 404;
  });
  protected readonly busy = signal(false);

  protected interviewType(type: InterviewType): string {
    return INTERVIEW_TYPE_LABELS[type];
  }

  protected interviewStatus(status: InterviewStatus): string {
    return INTERVIEW_STATUS_LABELS[status];
  }

  protected label(status: CandidateStatus): string {
    return CANDIDATE_STATUS_LABELS[status];
  }

  protected withdraw(application: CandidateApplication): void {
    this.dialog
      .open<WithdrawDialog, string, WithdrawDialogResult>(WithdrawDialog, {
        data: application.job.title,
        width: '480px',
      })
      .afterClosed()
      .subscribe((result) => {
        if (!result) {
          return;
        }
        this.busy.set(true);
        this.api.withdraw(application.id, result.comment).subscribe({
          next: (updated) => {
            this.busy.set(false);
            this.application.set(updated);
            this.notifications.success($localize`Candidature retirée.`);
          },
          error: (error: unknown) => {
            this.busy.set(false);
            // Statut devenu final entre-temps (rejet, embauche) : on montre l'état réel
            if (isApiError(error) && error.code === 'INVALID_STATUS_TRANSITION') {
              this.notifications.error($localize`Cette candidature ne peut plus être retirée.`);
              this.application.reload();
            }
          },
        });
      });
  }
}

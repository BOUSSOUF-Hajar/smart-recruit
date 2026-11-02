import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthStore } from '../../../core/auth/auth.store';
import { CANDIDATE_STATUS_LABELS, CandidateStatus } from '../../../shared/models/application';
import { INTERVIEW_TYPE_LABELS, InterviewType } from '../../../shared/models/interview';

/** Tableau de bord candidat (docs/api.md §5). */
export interface CandidateDashboard {
  applicationsByStatus: Partial<Record<CandidateStatus, number>>;
  upcomingInterviews: {
    id: string;
    startsAt: string;
    durationMinutes: number;
    type: InterviewType;
    jobTitle: string;
    companyName: string;
  }[];
  resume: { present: boolean; uploadedAt: string | null };
  profileCompleteness: { percent: number; missing: string[] };
}

/** Ordre d'affichage des statuts candidat. */
const ORDER: readonly CandidateStatus[] = [
  'SENT',
  'IN_REVIEW',
  'INTERVIEW',
  'OFFER_RECEIVED',
  'HIRED',
  'NOT_SELECTED',
  'WITHDRAWN',
];

/** US-45 : l'état de mes démarches en un coup d'œil, avec un appel à l'action s'il manque le CV. */
@Component({
  selector: 'app-candidate-dashboard-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ title }}</h1>
    @if (dashboard.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (dashboard.error()) {
      <p role="alert" i18n>Le tableau de bord n'a pas pu être chargé.</p>
    } @else if (data(); as d) {
      @if (!d.resume.present) {
        <section class="cta" role="note">
          <mat-icon aria-hidden="true">upload_file</mat-icon>
          <div>
            <strong i18n>Ajoutez votre CV</strong>
            <p i18n>Il est nécessaire pour postuler.</p>
          </div>
          <a mat-flat-button routerLink="/candidate/profile" [queryParams]="{ tab: 4 }" i18n
            >Téléverser mon CV</a
          >
        </section>
      }

      <div class="columns">
        <section class="card">
          <h2 i18n>Mes candidatures</h2>
          @if (statuses(d).length) {
            <ul class="plain">
              @for (item of statuses(d); track item.status) {
                <li>
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }}</strong>
                </li>
              }
            </ul>
          } @else {
            <p class="sub" i18n>Aucune candidature pour l'instant.</p>
          }
          <a mat-button routerLink="/candidate/applications" i18n>Voir mes candidatures</a>
          <a mat-button routerLink="/jobs" i18n>Chercher une offre</a>
        </section>

        <section class="card">
          <h2 i18n>Prochains entretiens</h2>
          <ul class="plain">
            @for (interview of d.upcomingInterviews; track interview.id) {
              <li>
                <span>
                  {{ interview.jobTitle
                  }}<span class="sub">
                    · {{ interview.companyName }} · {{ type(interview.type) }}</span
                  >
                </span>
                <time [attr.datetime]="interview.startsAt">{{
                  interview.startsAt | date: 'EEE d MMM, HH:mm'
                }}</time>
              </li>
            } @empty {
              <li class="sub" i18n>Aucun entretien prévu.</li>
            }
          </ul>
          <a mat-button routerLink="/candidate/interviews" i18n>Mes entretiens</a>
        </section>

        <section class="card">
          <h2 i18n>Mon profil</h2>
          <p class="percent">{{ d.profileCompleteness.percent }} %</p>
          <div
            class="bar"
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            [attr.aria-valuenow]="d.profileCompleteness.percent"
            aria-label="Complétude du profil"
            i18n-aria-label
          >
            <span [style.width.%]="d.profileCompleteness.percent"></span>
          </div>
          @if (d.resume.present && d.resume.uploadedAt) {
            <p class="sub" i18n>CV ajouté le {{ d.resume.uploadedAt | date: 'd MMMM y' }}</p>
          }
          <a mat-button routerLink="/candidate/profile" i18n>Compléter mon profil</a>
        </section>
      </div>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 16px;
      font: var(--mat-sys-headline-small);
    }
    h2 {
      margin: 0 0 12px;
      font: var(--mat-sys-title-medium);
    }
    .cta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-bottom: 16px;
      padding: 16px;
      border-radius: 12px;
      color: var(--mat-sys-on-tertiary-container);
      background: var(--mat-sys-tertiary-container);
    }
    .cta div {
      flex: 1;
    }
    .cta p {
      margin: 2px 0 0;
    }
    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 16px;
    }
    .card {
      padding: 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
      min-width: 0;
    }
    .plain {
      margin: 0 0 8px;
      padding: 0;
      list-style: none;
    }
    .plain li {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      padding: 6px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .percent {
      margin: 0;
      font: var(--mat-sys-display-small);
      color: var(--mat-sys-primary);
    }
    .bar {
      position: relative;
      height: 8px;
      margin: 8px 0;
      border-radius: 4px;
      background: var(--mat-sys-surface-container-highest);
    }
    .bar span {
      position: absolute;
      inset: 0 auto 0 0;
      border-radius: 4px;
      background: var(--mat-sys-primary);
    }
  `,
})
export class CandidateDashboardPage {
  protected readonly title = $localize`Espace candidat — bonjour ${inject(AuthStore).user()?.firstName ?? ''}:name:`;
  protected readonly dashboard = httpResource<CandidateDashboard>(
    () => '/api/v1/candidates/me/dashboard',
  );
  protected readonly data = computed(() =>
    this.dashboard.hasValue() ? this.dashboard.value() : undefined,
  );

  protected statuses(
    d: CandidateDashboard,
  ): { status: CandidateStatus; label: string; count: number }[] {
    return ORDER.filter((status) => d.applicationsByStatus[status]).map((status) => ({
      status,
      label: CANDIDATE_STATUS_LABELS[status],
      count: d.applicationsByStatus[status]!,
    }));
  }

  protected type(type: InterviewType): string {
    return INTERVIEW_TYPE_LABELS[type];
  }
}

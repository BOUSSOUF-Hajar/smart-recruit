import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthStore } from '../../../core/auth/auth.store';
import { APPLICATION_STATUS_LABELS, ApplicationStatus } from '../../../shared/models/application';
import { JOB_STATUS_LABELS, JobStatus } from '../../../shared/models/job';
import { StatTile } from '../../../shared/ui/stat-tile/stat-tile';
import { PIPELINE, RecruiterDashboard, daysAgo } from './recruiter-dashboard.models';

/**
 * US-44 : vue d'ensemble de l'entreprise. Chaque chiffre mène à la liste filtrée correspondante
 * (cahier des charges D8) ; les indicateurs sont calculés à la volée par le serveur.
 */
@Component({
  selector: 'app-recruiter-dashboard-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatProgressBarModule, StatTile],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>{{ title }}</h1>
    @if (dashboard.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (dashboard.error()) {
      <div class="state" role="alert">
        <p i18n>Le tableau de bord n'a pas pu être chargé.</p>
        <button mat-flat-button type="button" (click)="dashboard.reload()" i18n>Réessayer</button>
      </div>
    } @else if (data(); as d) {
      <section class="tiles" aria-label="Indicateurs" i18n-aria-label>
        <app-stat-tile
          [value]="d.applicationsReceived.last7Days"
          label="Candidatures sur 7 jours"
          i18n-label
          [hint]="last30(d)"
          link="/recruiter/applications"
          [params]="{ submittedFrom: since7 }"
        />
        <app-stat-tile
          [value]="d.inScreening"
          label="En présélection"
          i18n-label
          link="/recruiter/applications"
          [params]="{ status: 'SCREENING' }"
        />
        <app-stat-tile
          [value]="d.shortlisted"
          label="Retenues pour la suite"
          i18n-label
          link="/recruiter/applications"
          [params]="{ status: 'SHORTLISTED' }"
        />
        <app-stat-tile
          [value]="d.hires.last30Days"
          label="Embauches sur 30 jours"
          i18n-label
          link="/recruiter/applications"
          [params]="{ status: 'HIRED' }"
        />
      </section>

      <div class="columns">
        <section class="card">
          <h2 i18n>Offres</h2>
          <ul class="plain">
            @for (status of jobStatuses; track status) {
              <li>
                <a routerLink="/recruiter/jobs" [queryParams]="{ status }">{{
                  jobLabel(status)
                }}</a>
                <strong>{{ d.jobsByStatus[status] ?? 0 }}</strong>
              </li>
            }
          </ul>
          <a mat-button routerLink="/recruiter/jobs/new" i18n>Nouvelle offre</a>
        </section>

        <section class="card">
          <h2 i18n>Entretiens des 7 prochains jours</h2>
          <ul class="plain interviews">
            @for (interview of d.upcomingInterviews; track interview.id) {
              <li>
                <span>
                  <a [routerLink]="['/recruiter/applications', interview.applicationId]">{{
                    interview.candidateName
                  }}</a>
                  <span class="sub"> · {{ interview.jobTitle }}</span>
                </span>
                <time [attr.datetime]="interview.startsAt">{{
                  interview.startsAt | date: 'EEE d MMM, HH:mm'
                }}</time>
              </li>
            } @empty {
              <li class="sub" i18n>Aucun entretien prévu.</li>
            }
          </ul>
          <a mat-button routerLink="/recruiter/interviews" i18n>Agenda</a>
        </section>
      </div>

      <section class="card">
        <h2 i18n>Candidatures par offre</h2>
        @if (d.perJob.length) {
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th scope="col" i18n>Offre</th>
                  @for (status of pipeline; track status) {
                    <th scope="col">{{ applicationLabel(status) }}</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (job of d.perJob; track job.jobId) {
                  <tr>
                    <th scope="row">
                      <a [routerLink]="['/recruiter/jobs', job.jobId]">{{ job.title }}</a>
                      <span class="sub"> · {{ jobLabel(job.status) }}</span>
                    </th>
                    @for (status of pipeline; track status) {
                      <td>
                        @if (job.countsByStatus[status]; as count) {
                          <a
                            routerLink="/recruiter/applications"
                            [queryParams]="{ jobOfferId: job.jobId, status }"
                            >{{ count }}</a
                          >
                        } @else {
                          <span class="sub">0</span>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <p class="sub" i18n>Aucune offre active pour l'instant.</p>
        }
      </section>
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
    .tiles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 12px;
      margin-bottom: 16px;
    }
    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
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
    .table-wrapper {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      padding: 8px;
      text-align: left;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      white-space: nowrap;
    }
    td {
      text-align: center;
    }
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 32px 0;
    }
  `,
})
export class RecruiterDashboardPage {
  protected readonly title = $localize`Espace recruteur — bonjour ${inject(AuthStore).user()?.firstName ?? ''}:name:`;
  protected readonly dashboard = httpResource<RecruiterDashboard>(
    () => '/api/v1/company/dashboard',
  );
  protected readonly data = computed(() =>
    this.dashboard.hasValue() ? this.dashboard.value() : undefined,
  );

  protected readonly jobStatuses: readonly JobStatus[] = ['DRAFT', 'PUBLISHED', 'CLOSED'];
  protected readonly pipeline = PIPELINE;
  /**
   * Début de la liste filtrée : le serveur compte sur 7 × 24 h glissantes, le filtre de la liste
   * travaille en dates ; partir de J-7 garantit que la liste contient toutes les candidatures comptées.
   */
  protected readonly since7 = daysAgo(7);

  protected jobLabel(status: JobStatus): string {
    return JOB_STATUS_LABELS[status];
  }

  protected applicationLabel(status: ApplicationStatus): string {
    return APPLICATION_STATUS_LABELS[status];
  }

  protected last30(d: RecruiterDashboard): string {
    return $localize`${d.applicationsReceived.last30Days}:count: sur 30 jours`;
  }
}

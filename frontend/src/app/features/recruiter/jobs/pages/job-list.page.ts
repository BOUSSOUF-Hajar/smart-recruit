import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { JOB_STATUS_LABELS, Page, optionsOf } from '../../../../shared/models/job';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { JobStatusChip } from '../components/job-status';
import { RecruiterJobSummary, totalApplications } from '../data-access/recruiter-jobs.models';

/** US-24 : offres de l'entreprise, tous statuts ; archivées masquées sauf filtre explicite. */
@Component({
  selector: 'app-job-list-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    EmptyState,
    JobStatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <h1 i18n>Offres de l'entreprise</h1>
      <a mat-flat-button routerLink="/recruiter/jobs/new">
        <mat-icon>add</mat-icon>
        <span i18n>Nouvelle offre</span>
      </a>
    </div>

    <mat-form-field class="filter">
      <mat-label i18n>Statut</mat-label>
      <mat-select [value]="status() ?? ''" (selectionChange)="filter($event.value)">
        <mat-option value="" i18n>Toutes sauf archivées</mat-option>
        @for (option of statuses; track option.value) {
          <mat-option [value]="option.value">{{ option.label }}</mat-option>
        }
      </mat-select>
    </mat-form-field>

    @if (jobs.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (pageData(); as page) {
      @if (page.content.length) {
        <table mat-table [dataSource]="page.content" class="table">
          <ng-container matColumnDef="title">
            <th mat-header-cell *matHeaderCellDef i18n>Offre</th>
            <td mat-cell *matCellDef="let job">
              <a [routerLink]="['/recruiter/jobs', job.id]">{{ job.title }}</a>
              <div class="sub">{{ job.city }}</div>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef i18n>Statut</th>
            <td mat-cell *matCellDef="let job">
              <app-job-status [status]="job.status" [expired]="job.expired" />
            </td>
          </ng-container>
          <ng-container matColumnDef="applications">
            <th mat-header-cell *matHeaderCellDef i18n>Candidatures</th>
            <td mat-cell *matCellDef="let job">
              @if (total(job)) {
                <a
                  [routerLink]="['/recruiter/applications']"
                  [queryParams]="{ jobOfferId: job.id }"
                  >{{ total(job) }}</a
                >
              } @else {
                0
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="expiresAt">
            <th mat-header-cell *matHeaderCellDef i18n>Expiration</th>
            <td mat-cell *matCellDef="let job">
              {{ job.expiresAt ? (job.expiresAt | date: 'd MMM y') : '—' }}
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
        @if (page.totalElements > page.size) {
          <mat-paginator
            [length]="page.totalElements"
            [pageIndex]="page.page"
            [pageSize]="page.size"
            [hidePageSize]="true"
            (page)="goTo($event)"
          />
        }
      } @else {
        <app-empty-state
          icon="work_outline"
          title="Aucune offre"
          i18n-title
          message="Créez une première offre : elle restera en brouillon jusqu'à sa publication."
          i18n-message
        >
          <a mat-flat-button routerLink="/recruiter/jobs/new" i18n>Créer une offre</a>
        </app-empty-state>
      }
    } @else if (jobs.error()) {
      <p role="alert" i18n>Les offres n'ont pas pu être chargées.</p>
    }
  `,
  styles: `
    .header {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }
    .filter {
      margin-top: 16px;
      width: 260px;
    }
    .table {
      width: 100%;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class JobListPage {
  private readonly router = inject(Router);

  /** Paramètres de requête : filtre et page restent dans l'URL. */
  readonly status = input<string>();
  readonly page = input<string>();

  protected readonly columns = ['title', 'status', 'applications', 'expiresAt'];
  protected readonly statuses = optionsOf(JOB_STATUS_LABELS);

  protected readonly jobs = httpResource<Page<RecruiterJobSummary>>(() => {
    const params: Record<string, string> = { page: this.page() ?? '0' };
    const status = this.status();
    if (status) {
      params['status'] = status;
    }
    return { url: '/api/v1/company/jobs', params };
  });
  protected readonly pageData = computed(() =>
    this.jobs.hasValue() ? this.jobs.value() : undefined,
  );

  protected total(job: RecruiterJobSummary): number {
    return totalApplications(job.applicationCounts);
  }

  protected filter(status: string): void {
    void this.router.navigate([], { queryParams: { status: status || null, page: null } });
  }

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], {
      queryParams: { page: event.pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }
}

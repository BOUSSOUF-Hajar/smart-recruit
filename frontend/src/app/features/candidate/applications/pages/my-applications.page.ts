import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Page } from '../../../../shared/models/job';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { CandidateStatusChip } from '../components/candidate-status';
import { CandidateApplication } from '../data-access/candidate-applications.models';

/** US-28 : mes candidatures, les plus récentes d'abord, avec leur statut simplifié. */
@Component({
  selector: 'app-my-applications-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatPaginatorModule,
    MatProgressBarModule,
    EmptyState,
    CandidateStatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Mes candidatures</h1>

    @if (applications.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (applications.error()) {
      <div class="state" role="alert">
        <p i18n>Vos candidatures n'ont pas pu être chargées.</p>
        <button mat-flat-button type="button" (click)="applications.reload()" i18n>
          Réessayer
        </button>
      </div>
    } @else if (pageData(); as page) {
      <ul class="list">
        @for (application of page.content; track application.id) {
          <li>
            <a class="row" [routerLink]="['/candidate/applications', application.id]">
              <span class="main">
                <strong>{{ application.job.title }}</strong>
                <span class="sub"
                  >{{ application.job.companyName }} · envoyée le
                  {{ application.submittedAt | date: 'd MMM y' }}</span
                >
              </span>
              <app-candidate-status [status]="application.candidateStatus" />
            </a>
          </li>
        } @empty {
          <app-empty-state
            icon="send"
            title="Aucune candidature pour l'instant"
            i18n-title
            message="Trouvez une offre qui vous correspond et postulez en un clic."
            i18n-message
          >
            <a mat-flat-button routerLink="/jobs" i18n>Voir les offres</a>
          </app-empty-state>
        }
      </ul>
      @if (page.totalElements > page.size) {
        <mat-paginator
          [length]="page.totalElements"
          [pageIndex]="page.page"
          [pageSize]="page.size"
          [hidePageSize]="true"
          (page)="goTo($event)"
          aria-label="Pages de candidatures"
          i18n-aria-label
        />
      }
    }
  `,
  styles: `
    h1 {
      margin: 0 0 16px;
      font: var(--mat-sys-headline-small);
    }
    .list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 8px 16px;
      padding: 12px 16px;
      border-radius: 12px;
      color: inherit;
      text-decoration: none;
      background: var(--mat-sys-surface-container-low);
    }
    .row:hover,
    .row:focus-visible {
      background: var(--mat-sys-surface-container);
    }
    .main {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
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
export class MyApplicationsPage {
  private readonly router = inject(Router);

  /** Paramètre de requête `page`. */
  readonly page = input<string>();

  protected readonly applications = httpResource<Page<CandidateApplication>>(() => ({
    url: '/api/v1/candidates/me/applications',
    params: { page: this.page() ?? '0' },
  }));
  protected readonly pageData = computed(() =>
    this.applications.hasValue() ? this.applications.value() : undefined,
  );

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], { queryParams: { page: event.pageIndex || null } });
  }
}

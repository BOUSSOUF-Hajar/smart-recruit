import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthStore } from '../../../core/auth/auth.store';
import { Role } from '../../../core/auth/auth.models';
import { JOB_STATUS_LABELS, JobStatus } from '../../../shared/models/job';
import { StatTile } from '../../../shared/ui/stat-tile/stat-tile';
import { AdminDashboard, AiState } from '../data-access/admin.api';

const AI_LABELS: Record<AiState, string> = {
  AVAILABLE: $localize`Disponible`,
  UNAVAILABLE: $localize`Indisponible`,
  DISABLED: $localize`Désactivée`,
};

/**
 * US-46 : santé et usage de la plateforme. Les comptes et les entreprises mènent à leurs listes
 * filtrées ; offres et candidatures n'ont pas de liste côté admin (il ne voit aucun dossier).
 */
@Component({
  selector: 'app-admin-dashboard-page',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, StatTile],
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
      <section class="ai" [class.down]="d.ai.status === 'UNAVAILABLE'" role="status">
        <mat-icon aria-hidden="true">{{ aiIcon(d.ai.status) }}</mat-icon>
        <div>
          <strong i18n>Assistance IA : {{ aiLabel(d.ai.status) }}</strong>
          @if (d.ai.model) {
            <span class="sub"> · {{ d.ai.model }}</span>
          }
          @if (d.ai.status === 'UNAVAILABLE') {
            <p i18n>
              Le serveur de modèles ne répond pas : les recruteurs ne peuvent plus lancer d'analyse,
              le reste de la plateforme fonctionne.
            </p>
          }
        </div>
      </section>

      <h2 i18n>Comptes</h2>
      <section class="tiles" aria-label="Comptes" i18n-aria-label>
        @for (role of roles; track role.key) {
          <app-stat-tile
            [value]="d.usersByRole[role.key].active"
            [label]="role.label"
            [hint]="disabledHint(d.usersByRole[role.key].disabled)"
            link="/admin/users"
            [params]="{ role: role.key, enabled: 'true' }"
          />
        }
        <app-stat-tile
          [value]="d.companies.active"
          label="Entreprises actives"
          i18n-label
          [hint]="inactiveHint(d.companies.disabled)"
          link="/admin/companies"
          [params]="{ active: 'true' }"
        />
      </section>

      <h2 i18n>Activité</h2>
      <section class="tiles" aria-label="Activité" i18n-aria-label>
        @for (status of jobStatuses; track status) {
          <app-stat-tile [value]="d.jobsByStatus[status] ?? 0" [label]="jobLabel(status)" />
        }
        <app-stat-tile
          [value]="d.applications.total"
          label="Candidatures"
          i18n-label
          [hint]="last30(d.applications.last30Days)"
        />
      </section>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 16px;
      font: var(--mat-sys-headline-small);
    }
    h2 {
      margin: 24px 0 12px;
      font: var(--mat-sys-title-medium);
    }
    .tiles {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
      gap: 12px;
    }
    .ai {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .ai.down {
      color: var(--mat-sys-on-error-container);
      background: var(--mat-sys-error-container);
    }
    .ai p {
      margin: 4px 0 0;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .ai.down .sub {
      color: inherit;
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
export class AdminDashboardPage {
  protected readonly title = $localize`Administration — bonjour ${inject(AuthStore).user()?.firstName ?? ''}:name:`;
  protected readonly dashboard = httpResource<AdminDashboard>(() => '/api/v1/admin/dashboard');
  protected readonly data = computed(() =>
    this.dashboard.hasValue() ? this.dashboard.value() : undefined,
  );

  protected readonly roles: readonly { key: Role; label: string }[] = [
    { key: 'CANDIDATE', label: $localize`Candidats actifs` },
    { key: 'RECRUITER', label: $localize`Recruteurs actifs` },
    { key: 'ADMIN', label: $localize`Administrateurs actifs` },
  ];
  protected readonly jobStatuses: readonly JobStatus[] = [
    'PUBLISHED',
    'DRAFT',
    'CLOSED',
    'ARCHIVED',
  ];

  protected aiLabel(state: AiState): string {
    return AI_LABELS[state];
  }

  protected aiIcon(state: AiState): string {
    return state === 'AVAILABLE' ? 'check_circle' : state === 'UNAVAILABLE' ? 'error' : 'block';
  }

  protected jobLabel(status: JobStatus): string {
    return $localize`Offres : ${JOB_STATUS_LABELS[status].toLowerCase()}:status:`;
  }

  protected disabledHint(count: number): string {
    return $localize`${count}:count: désactivé(s)`;
  }

  protected inactiveHint(count: number): string {
    return $localize`${count}:count: inactive(s)`;
  }

  protected last30(count: number): string {
    return $localize`${count}:count: sur 30 jours`;
  }
}

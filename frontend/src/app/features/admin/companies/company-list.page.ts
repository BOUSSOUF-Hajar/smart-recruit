import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { NotificationService } from '../../../core/errors/notification.service';
import { Page } from '../../../shared/models/job';
import { confirm } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { CompanyDialog } from '../components/company-dialog';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, Company } from '../data-access/admin.api';

/** Message de confirmation de la désactivation : ce que la cascade va faire (RG-31). */
export function deactivationMessage(company: Company): string {
  return $localize`Ses ${company.publishedJobCount}:jobs: offre(s) publiée(s) seront clôturées et ses ${company.recruiterCount}:recruiters: recruteur(s) désactivés, sessions fermées. Les candidats gardent l'accès à leurs candidatures.`;
}

/** US-49 : entreprises clientes — recherche, création, modification, désactivation en cascade. */
@Component({
  selector: 'app-company-list-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    EmptyState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <h1 i18n>Entreprises</h1>
      <div class="actions">
        <a mat-button routerLink="/admin/users/new">
          <mat-icon>person_add</mat-icon>
          <span i18n>Créer un compte</span>
        </a>
        <button mat-flat-button type="button" (click)="create()">
          <mat-icon>add</mat-icon>
          <span i18n>Nouvelle entreprise</span>
        </button>
      </div>
    </div>

    <form class="filters" [formGroup]="form" (ngSubmit)="search()">
      <mat-form-field class="grow">
        <mat-label i18n>Nom</mat-label>
        <input matInput formControlName="q" />
        <button matSuffix mat-icon-button type="submit" aria-label="Rechercher" i18n-aria-label>
          <mat-icon>search</mat-icon>
        </button>
      </mat-form-field>
      <mat-button-toggle-group
        [value]="activeFilter()"
        (change)="filterBy($event.value)"
        aria-label="Filtrer par état"
        i18n-aria-label
      >
        <mat-button-toggle value="" i18n>Toutes</mat-button-toggle>
        <mat-button-toggle value="true" i18n>Actives</mat-button-toggle>
        <mat-button-toggle value="false" i18n>Inactives</mat-button-toggle>
      </mat-button-toggle-group>
    </form>

    @if (companies.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (companies.error()) {
      <p class="error" role="alert" i18n>Impossible de charger les entreprises.</p>
    }

    @if (pageData(); as data) {
      @if (data.content.length) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="data.content" class="table">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef i18n>Nom</th>
              <td mat-cell *matCellDef="let company">
                {{ company.name }}
                <div class="sub">{{ company.sector ?? '' }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="city">
              <th mat-header-cell *matHeaderCellDef i18n>Ville</th>
              <td mat-cell *matCellDef="let company">{{ location(company) }}</td>
            </ng-container>
            <ng-container matColumnDef="recruiters">
              <th mat-header-cell *matHeaderCellDef i18n>Recruteurs</th>
              <td mat-cell *matCellDef="let company">
                <a routerLink="/admin/users" [queryParams]="{ companyId: company.id }">{{
                  company.recruiterCount
                }}</a>
              </td>
            </ng-container>
            <ng-container matColumnDef="jobs">
              <th mat-header-cell *matHeaderCellDef i18n>Offres publiées</th>
              <td mat-cell *matCellDef="let company">
                {{ company.publishedJobCount }} <span class="sub">/ {{ company.jobCount }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="active">
              <th mat-header-cell *matHeaderCellDef i18n>État</th>
              <td mat-cell *matCellDef="let company">
                @if (company.active) {
                  <span i18n>Active</span>
                } @else {
                  <span class="inactive" i18n>Inactive</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>
                <span class="cdk-visually-hidden" i18n>Actions</span>
              </th>
              <td mat-cell *matCellDef="let company">
                <button
                  mat-icon-button
                  type="button"
                  [matMenuTriggerFor]="menu"
                  [attr.aria-label]="actionsLabel(company)"
                >
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #menu="matMenu">
                  <button mat-menu-item type="button" (click)="edit(company)">
                    <mat-icon>edit</mat-icon>
                    <span i18n>Modifier</span>
                  </button>
                  @if (company.active) {
                    <button mat-menu-item type="button" (click)="deactivate(company)">
                      <mat-icon>block</mat-icon>
                      <span i18n>Désactiver</span>
                    </button>
                  } @else {
                    <button mat-menu-item type="button" (click)="activate(company)">
                      <mat-icon>check_circle</mat-icon>
                      <span i18n>Réactiver</span>
                    </button>
                  }
                </mat-menu>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>
        <mat-paginator
          [length]="data.totalElements"
          [pageIndex]="data.page"
          [pageSize]="data.size"
          [hidePageSize]="true"
          (page)="goTo($event)"
        />
      } @else {
        <app-empty-state icon="apartment" [title]="emptyTitle" />
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
    .actions {
      display: flex;
      gap: 8px;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 16px;
      margin-top: 8px;
    }
    .grow {
      flex: 1 1 240px;
    }
    .table-wrapper {
      overflow-x: auto;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.875rem;
    }
    .inactive {
      color: var(--mat-sys-error);
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class CompanyListPage {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly active = input<string>();
  readonly q = input<string>();
  readonly page = input<string>();

  protected readonly columns = ['name', 'city', 'recruiters', 'jobs', 'active', 'actions'];
  protected readonly emptyTitle = $localize`Aucune entreprise pour ce filtre`;
  protected readonly activeFilter = computed(() => this.active() ?? '');

  protected readonly companies = httpResource<Page<Company>>(() => {
    const params: Record<string, string> = { size: '20' };
    if (this.active()) params['active'] = this.active()!;
    if (this.q()) params['q'] = this.q()!;
    if (this.page()) params['page'] = this.page()!;
    return { url: '/api/v1/admin/companies', params };
  });

  /** Dernière page connue : elle reste affichée pendant le rechargement. */
  protected readonly pageData = computed(() =>
    this.companies.hasValue() ? this.companies.value() : undefined,
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({ q: [''] });

  constructor() {
    effect(() => this.form.reset({ q: this.q() ?? '' }, { emitEvent: false }));
  }

  protected location(company: Company): string {
    return [company.city, company.countryCode].filter(Boolean).join(', ') || '—';
  }

  protected actionsLabel(company: Company): string {
    return $localize`Actions pour ${company.name}:name:`;
  }

  protected search(): void {
    void this.router.navigate([], {
      queryParams: { q: this.form.getRawValue().q.trim() || null, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected filterBy(active: string): void {
    void this.router.navigate([], {
      queryParams: { active: active || null, page: null },
      queryParamsHandling: 'merge',
    });
  }

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], {
      queryParams: { page: event.pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }

  protected create(): void {
    this.dialog
      .open<CompanyDialog, Company | null, Company>(CompanyDialog)
      .afterClosed()
      .subscribe((company) => {
        if (company) {
          this.notifications.success($localize`Entreprise « ${company.name}:name: » créée.`);
          this.companies.reload();
        }
      });
  }

  protected edit(company: Company): void {
    this.dialog
      .open<CompanyDialog, Company | null, Company>(CompanyDialog, { data: company })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) {
          this.notifications.success($localize`Entreprise « ${updated.name}:name: » mise à jour.`);
          this.companies.reload();
        }
      });
  }

  protected deactivate(company: Company): void {
    confirm(this.dialog, {
      title: $localize`Désactiver ${company.name}:name: ?`,
      message: deactivationMessage(company),
      confirmLabel: $localize`Désactiver`,
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deactivateCompany(company.id).subscribe({
        next: (result) => {
          this.notifications.success(
            $localize`Entreprise désactivée : ${result.closedJobs}:jobs: offre(s) clôturée(s), ${result.disabledRecruiters}:recruiters: recruteur(s) désactivé(s).`,
          );
          this.companies.reload();
        },
        error: (error: unknown) => notifyFailure(this.notifications, error),
      });
    });
  }

  protected activate(company: Company): void {
    this.api.activateCompany(company.id).subscribe({
      next: () => {
        this.notifications.success(
          $localize`Entreprise réactivée. Ses recruteurs restent désactivés : réactivez ceux qui doivent revenir.`,
        );
        this.companies.reload();
      },
      error: (error: unknown) => notifyFailure(this.notifications, error),
    });
  }
}

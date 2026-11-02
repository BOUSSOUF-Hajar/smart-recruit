import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { AuthStore } from '../../../core/auth/auth.store';
import { Role } from '../../../core/auth/auth.models';
import { NotificationService } from '../../../core/errors/notification.service';
import { Page } from '../../../shared/models/job';
import { confirm } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { UserDialog, UserDialogData } from '../components/user-dialog';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, AdminUser, Company, ROLE_LABELS } from '../data-access/admin.api';

/** Paramètres de l'API à partir de l'URL : un filtre vide n'est pas envoyé. */
export function userParams(filters: {
  role?: string;
  companyId?: string;
  enabled?: string;
  q?: string;
  page?: string;
}): Record<string, string> {
  const params: Record<string, string> = { size: '20' };
  for (const [key, value] of Object.entries(filters)) {
    if (value) {
      params[key] = value;
    }
  }
  return params;
}

/**
 * US-48 : recherche des comptes, modification, désactivation et réactivation. Les filtres vivent
 * dans l'URL : un indicateur du tableau de bord ouvre la liste déjà filtrée.
 */
@Component({
  selector: 'app-user-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    EmptyState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <h1 i18n>Utilisateurs</h1>
      <a mat-flat-button routerLink="/admin/users/new">
        <mat-icon>person_add</mat-icon>
        <span i18n>Créer un compte</span>
      </a>
    </div>

    <form class="filters" [formGroup]="form" (ngSubmit)="apply()">
      <mat-form-field class="grow">
        <mat-label i18n>Nom ou email</mat-label>
        <input matInput formControlName="q" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Rôle</mat-label>
        <mat-select formControlName="role">
          <mat-option value="" i18n>Tous</mat-option>
          @for (role of roles; track role) {
            <mat-option [value]="role">{{ roleLabel(role) }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Entreprise</mat-label>
        <mat-select formControlName="companyId">
          <mat-option value="" i18n>Toutes</mat-option>
          @for (company of companyOptions(); track company.id) {
            <mat-option [value]="company.id">{{ company.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>État</mat-label>
        <mat-select formControlName="enabled">
          <mat-option value="" i18n>Tous</mat-option>
          <mat-option value="true" i18n>Actifs</mat-option>
          <mat-option value="false" i18n>Désactivés</mat-option>
        </mat-select>
      </mat-form-field>
      <div class="buttons">
        <button mat-flat-button type="submit" i18n>Filtrer</button>
        <button mat-button type="button" (click)="reset()" i18n>Effacer</button>
      </div>
    </form>

    @if (users.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (users.error()) {
      <p class="error" role="alert" i18n>Impossible de charger les utilisateurs.</p>
    }

    @if (pageData(); as data) {
      @if (data.content.length) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="data.content">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef i18n>Nom</th>
              <td mat-cell *matCellDef="let user">
                {{ user.firstName }} {{ user.lastName }}
                <div class="sub">{{ user.email }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="role">
              <th mat-header-cell *matHeaderCellDef i18n>Rôle</th>
              <td mat-cell *matCellDef="let user">
                {{ roleLabel(user.role) }}
                @if (user.companyName) {
                  <div class="sub">{{ user.companyName }}</div>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="lastLogin">
              <th mat-header-cell *matHeaderCellDef i18n>Dernière connexion</th>
              <td mat-cell *matCellDef="let user">
                @if (user.lastLoginAt) {
                  {{ user.lastLoginAt | date: 'd MMM y, HH:mm' }}
                } @else {
                  <span class="sub" i18n>Jamais</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="state">
              <th mat-header-cell *matHeaderCellDef i18n>État</th>
              <td mat-cell *matCellDef="let user">
                @if (user.enabled) {
                  <span i18n>Actif</span>
                } @else {
                  <span class="disabled" i18n>Désactivé</span>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>
                <span class="cdk-visually-hidden" i18n>Actions</span>
              </th>
              <td mat-cell *matCellDef="let user">
                <button
                  mat-icon-button
                  type="button"
                  [matMenuTriggerFor]="menu"
                  [attr.aria-label]="actionsLabel(user)"
                >
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #menu="matMenu">
                  <button mat-menu-item type="button" (click)="edit(user)">
                    <mat-icon>edit</mat-icon>
                    <span i18n>Modifier</span>
                  </button>
                  @if (user.enabled) {
                    <button
                      mat-menu-item
                      type="button"
                      (click)="setEnabled(user, false)"
                      [disabled]="isMe(user)"
                    >
                      <mat-icon>block</mat-icon>
                      <span i18n>Désactiver</span>
                    </button>
                  } @else {
                    <button mat-menu-item type="button" (click)="setEnabled(user, true)">
                      <mat-icon>check_circle</mat-icon>
                      <span i18n>Réactiver</span>
                    </button>
                  }
                </mat-menu>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" [class.muted]="!row.enabled"></tr>
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
        <app-empty-state icon="person_search" [title]="emptyTitle" />
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
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 12px;
      margin-top: 8px;
    }
    .grow {
      flex: 1 1 220px;
    }
    .buttons {
      display: flex;
      gap: 8px;
    }
    .table-wrapper {
      overflow-x: auto;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.875rem;
    }
    .disabled {
      color: var(--mat-sys-error);
    }
    .muted td {
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class UserListPage {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);
  private readonly me = inject(AuthStore).user;

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly role = input<string>();
  readonly companyId = input<string>();
  readonly enabled = input<string>();
  readonly q = input<string>();
  readonly page = input<string>();

  protected readonly roles: readonly Role[] = ['CANDIDATE', 'RECRUITER', 'ADMIN'];
  protected readonly columns = ['name', 'role', 'lastLogin', 'state', 'actions'];
  protected readonly emptyTitle = $localize`Aucun utilisateur pour ces filtres`;

  private readonly filters = computed(() => ({
    role: this.role(),
    companyId: this.companyId(),
    enabled: this.enabled(),
    q: this.q(),
    page: this.page(),
  }));

  protected readonly users = httpResource<Page<AdminUser>>(() => ({
    url: '/api/v1/admin/users',
    params: userParams(this.filters()),
  }));
  protected readonly pageData = computed(() =>
    this.users.hasValue() ? this.users.value() : undefined,
  );

  protected readonly companies = httpResource<Page<Company>>(() => ({
    url: '/api/v1/admin/companies',
    params: { size: 100 },
  }));
  protected readonly companyOptions = computed(() =>
    this.companies.hasValue() ? this.companies.value().content : [],
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    q: [''],
    role: [''],
    companyId: [''],
    enabled: [''],
  });

  constructor() {
    // Le formulaire reflète l'URL (lien du tableau de bord, bouton Précédent)
    effect(() => {
      const filters = this.filters();
      this.form.reset(
        {
          q: filters.q ?? '',
          role: filters.role ?? '',
          companyId: filters.companyId ?? '',
          enabled: filters.enabled ?? '',
        },
        { emitEvent: false },
      );
    });
  }

  protected roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  protected actionsLabel(user: AdminUser): string {
    return $localize`Actions pour ${user.firstName}:first: ${user.lastName}:last:`;
  }

  /** Un admin ne se désactive pas lui-même (RG-29) : le serveur refuse aussi. */
  protected isMe(user: AdminUser): boolean {
    return user.id === this.me()?.id;
  }

  protected apply(): void {
    const value = this.form.getRawValue();
    void this.router.navigate([], {
      queryParams: {
        q: value.q.trim() || null,
        role: value.role || null,
        companyId: value.companyId || null,
        enabled: value.enabled || null,
        page: null,
      },
    });
  }

  protected reset(): void {
    void this.router.navigate([]);
  }

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], {
      queryParams: { page: event.pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }

  protected edit(user: AdminUser): void {
    this.dialog
      .open<UserDialog, UserDialogData, AdminUser>(UserDialog, {
        data: { user, companies: this.companyOptions() },
      })
      .afterClosed()
      .subscribe((updated) => {
        if (updated) {
          this.notifications.success($localize`Compte mis à jour.`);
          this.users.reload();
        }
      });
  }

  protected setEnabled(user: AdminUser, enabled: boolean): void {
    const name = `${user.firstName} ${user.lastName}`;
    const confirmation = enabled
      ? confirm(this.dialog, {
          title: $localize`Réactiver ${name}:name: ?`,
          message: $localize`Le compte pourra de nouveau se connecter.`,
          confirmLabel: $localize`Réactiver`,
        })
      : confirm(this.dialog, {
          title: $localize`Désactiver ${name}:name: ?`,
          message: $localize`Ses sessions sont fermées immédiatement et il ne pourra plus se connecter. Ses données sont conservées.`,
          confirmLabel: $localize`Désactiver`,
        });
    confirmation.subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.setUserEnabled(user.id, enabled).subscribe({
        next: () => {
          this.notifications.success(
            enabled ? $localize`Compte réactivé.` : $localize`Compte désactivé.`,
          );
          this.users.reload();
        },
        error: (error: unknown) => notifyFailure(this.notifications, error),
      });
    });
  }
}

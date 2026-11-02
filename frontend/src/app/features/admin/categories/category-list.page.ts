import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { filter, switchMap } from 'rxjs';
import { isApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { confirm } from '../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { askName } from '../components/name-dialog';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, AdminCategory } from '../data-access/admin.api';

/**
 * US-50 : catégories d'offres. RG-30 : une catégorie utilisée ne se supprime pas ; si l'admin
 * essaie, l'écran lui propose de la désactiver à la place.
 */
@Component({
  selector: 'app-category-list-page',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    MatTableModule,
    EmptyState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="header">
      <h1 i18n>Catégories d'offres</h1>
      <button mat-flat-button type="button" (click)="create()">
        <mat-icon>add</mat-icon>
        <span i18n>Nouvelle catégorie</span>
      </button>
    </div>
    <p class="sub" i18n>
      Une catégorie désactivée n'est plus proposée pour les nouvelles offres ; les offres existantes
      la gardent.
    </p>

    @if (categories.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (categories.error()) {
      <p class="error" role="alert" i18n>Impossible de charger les catégories.</p>
    }
    @if (rows(); as list) {
      @if (list.length) {
        <table mat-table [dataSource]="list">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef i18n>Nom</th>
            <td mat-cell *matCellDef="let category">{{ category.name }}</td>
          </ng-container>
          <ng-container matColumnDef="offers">
            <th mat-header-cell *matHeaderCellDef i18n>Offres</th>
            <td mat-cell *matCellDef="let category">{{ category.offerCount }}</td>
          </ng-container>
          <ng-container matColumnDef="state">
            <th mat-header-cell *matHeaderCellDef i18n>État</th>
            <td mat-cell *matCellDef="let category">
              @if (category.active) {
                <span i18n>Active</span>
              } @else {
                <span class="inactive" i18n>Désactivée</span>
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>
              <span class="cdk-visually-hidden" i18n>Actions</span>
            </th>
            <td mat-cell *matCellDef="let category">
              <button
                mat-icon-button
                type="button"
                [matMenuTriggerFor]="menu"
                [attr.aria-label]="actionsLabel(category)"
              >
                <mat-icon>more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item type="button" (click)="rename(category)">
                  <mat-icon>edit</mat-icon>
                  <span i18n>Renommer</span>
                </button>
                <button mat-menu-item type="button" (click)="setActive(category, !category.active)">
                  <mat-icon>{{ category.active ? 'block' : 'check_circle' }}</mat-icon>
                  <span>{{ category.active ? deactivateLabel : activateLabel }}</span>
                </button>
                <button mat-menu-item type="button" (click)="remove(category)">
                  <mat-icon>delete</mat-icon>
                  <span i18n>Supprimer</span>
                </button>
              </mat-menu>
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      } @else {
        <app-empty-state icon="category" [title]="emptyTitle" />
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
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .inactive,
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class CategoryListPage {
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);

  protected readonly columns = ['name', 'offers', 'state', 'actions'];
  protected readonly emptyTitle = $localize`Aucune catégorie`;
  protected readonly activateLabel = $localize`Réactiver`;
  protected readonly deactivateLabel = $localize`Désactiver`;

  protected readonly categories = httpResource<AdminCategory[]>(() => '/api/v1/admin/categories');
  protected readonly rows = computed(() =>
    this.categories.hasValue() ? this.categories.value() : null,
  );

  protected actionsLabel(category: AdminCategory): string {
    return $localize`Actions pour ${category.name}:name:`;
  }

  protected create(): void {
    askName(this.dialog, {
      title: $localize`Nouvelle catégorie`,
      label: $localize`Nom`,
      value: '',
      confirmLabel: $localize`Créer`,
    })
      .pipe(
        filter((name): name is string => !!name),
        switchMap((name) => this.api.createCategory(name)),
      )
      .subscribe({
        next: (category) => this.done($localize`Catégorie « ${category.name}:name: » créée.`),
        error: (error: unknown) => notifyFailure(this.notifications, error),
      });
  }

  protected rename(category: AdminCategory): void {
    askName(this.dialog, {
      title: $localize`Renommer la catégorie`,
      label: $localize`Nom`,
      value: category.name,
      confirmLabel: $localize`Renommer`,
    })
      .pipe(
        filter((name): name is string => !!name && name !== category.name),
        switchMap((name) => this.api.renameCategory(category.id, name)),
      )
      .subscribe({
        next: () => this.done($localize`Catégorie renommée.`),
        error: (error: unknown) => notifyFailure(this.notifications, error),
      });
  }

  protected setActive(category: AdminCategory, active: boolean): void {
    this.api.setCategoryActive(category.id, active).subscribe({
      next: () =>
        this.done(active ? $localize`Catégorie réactivée.` : $localize`Catégorie désactivée.`),
      error: (error: unknown) => notifyFailure(this.notifications, error),
    });
  }

  /** RESOURCE_IN_USE : la catégorie sert à des offres ; on propose la désactivation (RG-30). */
  protected remove(category: AdminCategory): void {
    confirm(this.dialog, {
      title: $localize`Supprimer « ${category.name}:name: » ?`,
      message: $localize`La suppression est définitive.`,
      confirmLabel: $localize`Supprimer`,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.deleteCategory(category.id)),
      )
      .subscribe({
        next: () => this.done($localize`Catégorie supprimée.`),
        error: (error: unknown) => {
          if (isApiError(error) && error.code === 'RESOURCE_IN_USE' && category.active) {
            this.proposeDeactivation(category, error.detail);
          } else {
            notifyFailure(this.notifications, error);
          }
        },
      });
  }

  private proposeDeactivation(category: AdminCategory, detail: string | undefined): void {
    confirm(this.dialog, {
      title: $localize`Catégorie utilisée`,
      message:
        detail ?? $localize`Cette catégorie est utilisée par des offres : désactivez-la plutôt.`,
      confirmLabel: $localize`Désactiver`,
    })
      .pipe(filter(Boolean))
      .subscribe(() => this.setActive(category, false));
  }

  private done(message: string): void {
    this.notifications.success(message);
    this.categories.reload();
  }
}

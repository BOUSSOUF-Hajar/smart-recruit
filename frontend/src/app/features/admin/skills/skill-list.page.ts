import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
import { filter, switchMap } from 'rxjs';
import { NotificationService } from '../../../core/errors/notification.service';
import { Page } from '../../../shared/models/job';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { MergeSkillDialog } from '../components/merge-skill-dialog';
import { askName } from '../components/name-dialog';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, AdminSkill, SkillMerge } from '../data-access/admin.api';

/** US-51 : référentiel de compétences — recherche (nom ou alias), renommage, fusion, désactivation. */
@Component({
  selector: 'app-skill-list-page',
  imports: [
    ReactiveFormsModule,
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
    <h1 i18n>Compétences</h1>
    <p class="sub" i18n>
      Fusionnez les doublons (« springboot » dans « Spring Boot ») : profils, offres et matching
      s'appuient sur la même compétence.
    </p>

    <form class="filters" [formGroup]="form" (ngSubmit)="search()">
      <mat-form-field class="grow">
        <mat-label i18n>Nom ou alias</mat-label>
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
        <mat-button-toggle value="false" i18n>Désactivées</mat-button-toggle>
      </mat-button-toggle-group>
    </form>

    @if (skills.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (skills.error()) {
      <p class="error" role="alert" i18n>Impossible de charger les compétences.</p>
    }
    @if (pageData(); as data) {
      @if (data.content.length) {
        <div class="table-wrapper">
          <table mat-table [dataSource]="data.content">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef i18n>Nom</th>
              <td mat-cell *matCellDef="let skill">
                {{ skill.name }}
                @if (skill.aliases.length) {
                  <div class="sub" i18n>Alias : {{ skill.aliases.join(', ') }}</div>
                }
              </td>
            </ng-container>
            <ng-container matColumnDef="usage">
              <th mat-header-cell *matHeaderCellDef i18n>Utilisations</th>
              <td mat-cell *matCellDef="let skill">{{ skill.usageCount }}</td>
            </ng-container>
            <ng-container matColumnDef="state">
              <th mat-header-cell *matHeaderCellDef i18n>État</th>
              <td mat-cell *matCellDef="let skill">
                @if (skill.active) {
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
              <td mat-cell *matCellDef="let skill">
                <button
                  mat-icon-button
                  type="button"
                  [matMenuTriggerFor]="menu"
                  [attr.aria-label]="actionsLabel(skill)"
                >
                  <mat-icon>more_vert</mat-icon>
                </button>
                <mat-menu #menu="matMenu">
                  <button mat-menu-item type="button" (click)="rename(skill)">
                    <mat-icon>edit</mat-icon>
                    <span i18n>Renommer</span>
                  </button>
                  <button mat-menu-item type="button" (click)="merge(skill)">
                    <mat-icon>merge</mat-icon>
                    <span i18n>Fusionner dans…</span>
                  </button>
                  <button mat-menu-item type="button" (click)="setActive(skill, !skill.active)">
                    <mat-icon>{{ skill.active ? 'block' : 'check_circle' }}</mat-icon>
                    <span>{{ skill.active ? deactivateLabel : activateLabel }}</span>
                  </button>
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
        <app-empty-state icon="psychology" [title]="emptyTitle" />
      }
    }
  `,
  styles: `
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.875rem;
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 16px;
    }
    .grow {
      flex: 1 1 240px;
    }
    .table-wrapper {
      overflow-x: auto;
    }
    .inactive,
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class SkillListPage {
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly q = input<string>();
  readonly active = input<string>();
  readonly page = input<string>();

  protected readonly columns = ['name', 'usage', 'state', 'actions'];
  protected readonly emptyTitle = $localize`Aucune compétence pour cette recherche`;
  protected readonly activateLabel = $localize`Réactiver`;
  protected readonly deactivateLabel = $localize`Désactiver`;
  protected readonly activeFilter = computed(() => this.active() ?? '');

  protected readonly skills = httpResource<Page<AdminSkill>>(() => {
    const params: Record<string, string> = { size: '20' };
    if (this.q()) params['q'] = this.q()!;
    if (this.active()) params['active'] = this.active()!;
    if (this.page()) params['page'] = this.page()!;
    return { url: '/api/v1/admin/skills', params };
  });
  protected readonly pageData = computed(() =>
    this.skills.hasValue() ? this.skills.value() : undefined,
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({ q: [''] });

  constructor() {
    effect(() => this.form.reset({ q: this.q() ?? '' }, { emitEvent: false }));
  }

  protected actionsLabel(skill: AdminSkill): string {
    return $localize`Actions pour ${skill.name}:name:`;
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

  protected rename(skill: AdminSkill): void {
    askName(this.dialog, {
      title: $localize`Renommer la compétence`,
      label: $localize`Nom`,
      value: skill.name,
      confirmLabel: $localize`Renommer`,
    })
      .pipe(
        filter((name): name is string => !!name && name !== skill.name),
        switchMap((name) => this.api.renameSkill(skill.id, name)),
      )
      .subscribe({
        next: () =>
          this.done($localize`Compétence renommée ; l'ancien nom reste reconnu comme alias.`),
        error: (error: unknown) => notifyFailure(this.notifications, error),
      });
  }

  protected merge(skill: AdminSkill): void {
    this.dialog
      .open<MergeSkillDialog, AdminSkill, SkillMerge>(MergeSkillDialog, { data: skill })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.done(
            $localize`« ${skill.name}:source: » fusionnée dans « ${result.target.name}:target: » : ${result.references}:count: référence(s) reportée(s).`,
          );
        }
      });
  }

  protected setActive(skill: AdminSkill, active: boolean): void {
    this.api.setSkillActive(skill.id, active).subscribe({
      next: () =>
        this.done(active ? $localize`Compétence réactivée.` : $localize`Compétence désactivée.`),
      error: (error: unknown) => notifyFailure(this.notifications, error),
    });
  }

  private done(message: string): void {
    this.notifications.success(message);
    this.skills.reload();
  }
}

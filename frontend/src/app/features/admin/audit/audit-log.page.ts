import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Page } from '../../../shared/models/job';
import { EmptyState } from '../../../shared/ui/empty-state/empty-state';
import { AuditEntry } from '../data-access/admin.api';
import { auditParams } from './audit-params';

/**
 * US-53 : qui a fait quoi, quand et d'où. Lecture seule : l'écran n'offre aucune action sur une
 * entrée, et l'API comme la base refusent toute modification.
 */
@Component({
  selector: 'app-audit-log-page',
  imports: [
    DatePipe,
    JsonPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    EmptyState,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Journal d'audit</h1>

    <form class="filters" [formGroup]="form" (ngSubmit)="apply()">
      <mat-form-field>
        <mat-label i18n>Du</mat-label>
        <input matInput type="date" formControlName="from" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Au</mat-label>
        <input matInput type="date" formControlName="to" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Action</mat-label>
        <mat-select formControlName="action">
          <mat-option value="" i18n>Toutes</mat-option>
          @for (action of actionOptions(); track action) {
            <mat-option [value]="action">{{ action }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Résultat</mat-label>
        <mat-select formControlName="result">
          <mat-option value="" i18n>Tous</mat-option>
          <mat-option value="SUCCESS" i18n>Réussite</mat-option>
          <mat-option value="FAILURE" i18n>Échec</mat-option>
        </mat-select>
      </mat-form-field>
      <div class="buttons">
        <button mat-flat-button type="submit" i18n>Filtrer</button>
        <button mat-button type="button" (click)="reset()" i18n>Effacer</button>
      </div>
    </form>
    @if (actorId()) {
      <mat-chip-set aria-label="Filtre par auteur" i18n-aria-label>
        <mat-chip (removed)="filterByActor(null)">
          <span i18n>Auteur : {{ actorLabel() }}</span>
          <button matChipRemove aria-label="Retirer le filtre par auteur" i18n-aria-label>
            <mat-icon>cancel</mat-icon>
          </button>
        </mat-chip>
      </mat-chip-set>
    }

    @if (entries.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (entries.error()) {
      <p class="error" role="alert" i18n>Impossible de charger le journal.</p>
    }
    @if (pageData(); as data) {
      @if (data.content.length) {
        <ul class="entries">
          @for (entry of data.content; track entry.id) {
            <li [class.failure]="entry.result === 'FAILURE'">
              <div class="line">
                <time [attr.datetime]="entry.occurredAt">{{
                  entry.occurredAt | date: 'd MMM y, HH:mm:ss'
                }}</time>
                <strong class="action">{{ entry.action }}</strong>
                <span class="result">{{
                  entry.result === 'SUCCESS' ? successLabel : failureLabel
                }}</span>
              </div>
              <div class="line sub">
                @if (entry.actorId) {
                  <button mat-button type="button" class="actor" (click)="filterByActor(entry)">
                    {{ entry.actorName ?? entry.actorEmail }}
                  </button>
                } @else {
                  <span>{{ entry.actorEmail ?? systemLabel }}</span>
                }
                @if (entry.resourceType) {
                  <span>· {{ entry.resourceType }} {{ entry.resourceId?.slice(0, 8) }}</span>
                }
                @if (entry.ipAddress) {
                  <span>· {{ entry.ipAddress }}</span>
                }
              </div>
              @if (entry.details) {
                <details>
                  <summary i18n>Détails</summary>
                  <pre>{{ entry.details | json }}</pre>
                </details>
              }
            </li>
          }
        </ul>
        <mat-paginator
          [length]="data.totalElements"
          [pageIndex]="data.page"
          [pageSize]="data.size"
          [hidePageSize]="true"
          (page)="goTo($event)"
        />
      } @else {
        <app-empty-state icon="history" [title]="emptyTitle" />
      }
    }
  `,
  styles: `
    .filters {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: 0 12px;
    }
    .buttons {
      display: flex;
      gap: 8px;
    }
    .entries {
      margin: 8px 0;
      padding: 0;
      list-style: none;
    }
    .entries li {
      padding: 10px 12px;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
      border-left: 4px solid transparent;
    }
    .entries li.failure {
      border-left-color: var(--mat-sys-error);
    }
    .line {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 4px 12px;
    }
    .action {
      font-family: monospace;
    }
    .failure .result {
      color: var(--mat-sys-error);
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.875rem;
    }
    .actor {
      min-width: 0;
      padding: 0 4px;
    }
    pre {
      margin: 4px 0 0;
      white-space: pre-wrap;
      word-break: break-word;
      font-size: 0.8rem;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
})
export class AuditLogPage {
  private readonly router = inject(Router);

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly from = input<string>();
  readonly to = input<string>();
  readonly action = input<string>();
  readonly result = input<string>();
  readonly actorId = input<string>();
  readonly actor = input<string>();
  readonly page = input<string>();

  protected readonly emptyTitle = $localize`Aucune entrée pour ces filtres`;
  protected readonly successLabel = $localize`Réussite`;
  protected readonly failureLabel = $localize`Échec`;
  protected readonly systemLabel = $localize`Système`;
  protected readonly actorLabel = computed(() => this.actor() ?? this.actorId()?.slice(0, 8));

  private readonly filters = computed(() => ({
    from: this.from(),
    to: this.to(),
    action: this.action(),
    result: this.result(),
    actorId: this.actorId(),
    page: this.page(),
  }));

  protected readonly entries = httpResource<Page<AuditEntry>>(() => ({
    url: '/api/v1/admin/audit-logs',
    params: auditParams(this.filters()),
  }));
  protected readonly pageData = computed(() =>
    this.entries.hasValue() ? this.entries.value() : undefined,
  );

  protected readonly actions = httpResource<string[]>(() => '/api/v1/admin/audit-logs/actions');
  protected readonly actionOptions = computed(() =>
    this.actions.hasValue() ? this.actions.value() : [],
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    from: [''],
    to: [''],
    action: [''],
    result: [''],
  });

  constructor() {
    effect(() => {
      const filters = this.filters();
      this.form.reset(
        {
          from: filters.from ?? '',
          to: filters.to ?? '',
          action: filters.action ?? '',
          result: filters.result ?? '',
        },
        { emitEvent: false },
      );
    });
  }

  protected apply(): void {
    const value = this.form.getRawValue();
    void this.router.navigate([], {
      queryParams: {
        from: value.from || null,
        to: value.to || null,
        action: value.action || null,
        result: value.result || null,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
  }

  protected reset(): void {
    void this.router.navigate([]);
  }

  protected filterByActor(entry: AuditEntry | null): void {
    void this.router.navigate([], {
      queryParams: {
        actorId: entry?.actorId ?? null,
        actor: entry ? (entry.actorName ?? entry.actorEmail) : null,
        page: null,
      },
      queryParamsHandling: 'merge',
    });
  }

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], {
      queryParams: { page: event.pageIndex || null },
      queryParamsHandling: 'merge',
    });
  }
}

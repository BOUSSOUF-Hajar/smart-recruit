import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Category } from '../../../../core/referential/referential.api';
import {
  CONTRACT_TYPE_LABELS,
  EXPERIENCE_LEVEL_LABELS,
  Page,
  WORK_MODE_LABELS,
  optionsOf,
} from '../../../../shared/models/job';
import { FRENCH_PAGINATOR } from '../../../../core/i18n/french-paginator-intl';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { JobCard } from '../components/job-card';
import { JobCard as JobCardModel, searchParams } from '../data-access/public-jobs.models';

/**
 * US-25 : recherche d'offres. Les filtres vivent dans l'URL : un lien copié ou le bouton Précédent
 * restaurent exactement la même liste. Changer un filtre ramène à la première page.
 */
@Component({
  selector: 'app-job-search-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    EmptyState,
    JobCard,
  ],
  providers: [FRENCH_PAGINATOR],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Offres d'emploi</h1>

    <form class="filters" [formGroup]="form" (ngSubmit)="apply()" role="search">
      <mat-form-field class="wide">
        <mat-label i18n>Mots-clés</mat-label>
        <input matInput formControlName="q" placeholder="développeur Java" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Ville</mat-label>
        <input matInput formControlName="city" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Catégorie</mat-label>
        <mat-select formControlName="categoryId">
          <mat-option value="" i18n>Toutes</mat-option>
          @for (category of categories.value() ?? []; track category.id) {
            <mat-option [value]="category.id">{{ category.name }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Contrat</mat-label>
        <mat-select formControlName="contractType" multiple>
          @for (option of contractTypes; track option.value) {
            <mat-option [value]="option.value">{{ option.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Mode de travail</mat-label>
        <mat-select formControlName="workMode" multiple>
          @for (option of workModes; track option.value) {
            <mat-option [value]="option.value">{{ option.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Niveau</mat-label>
        <mat-select formControlName="experienceLevel" multiple>
          @for (option of levels; track option.value) {
            <mat-option [value]="option.value">{{ option.label }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Salaire minimum (MAD)</mat-label>
        <input matInput formControlName="salaryMin" type="number" min="0" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Trier par</mat-label>
        <mat-select formControlName="sort">
          <mat-option value="" i18n>Plus récentes</mat-option>
          <mat-option value="salaryMax,desc" i18n>Salaire le plus élevé</mat-option>
        </mat-select>
      </mat-form-field>
      <div class="actions">
        <button mat-button type="button" (click)="reset()" i18n>Effacer</button>
        <button mat-flat-button type="submit" i18n>Rechercher</button>
      </div>
    </form>

    @if (results.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (results.error()) {
      <div class="state" role="alert">
        <p i18n>La recherche n'a pas pu aboutir.</p>
        <button mat-flat-button type="button" (click)="results.reload()" i18n>Réessayer</button>
      </div>
    } @else if (pageData(); as page) {
      <p class="count" aria-live="polite" i18n>{{ page.totalElements }} offre(s)</p>
      @for (job of page.content; track job.id) {
        <app-job-card [job]="job" />
      } @empty {
        <app-empty-state
          icon="search_off"
          title="Aucune offre ne correspond"
          i18n-title
          message="Essayez d'élargir vos critères."
          i18n-message
        />
      }
      @if (page.totalElements > page.size) {
        <mat-paginator
          [length]="page.totalElements"
          [pageIndex]="page.page"
          [pageSize]="page.size"
          [hidePageSize]="true"
          (page)="goTo($event)"
          aria-label="Pages de résultats"
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
    .filters {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 0 12px;
      margin-bottom: 8px;
    }
    .wide {
      grid-column: span 2;
    }
    .actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .count {
      color: var(--mat-sys-on-surface-variant);
    }
    app-job-card {
      display: block;
      margin-bottom: 12px;
    }
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 32px 0;
    }
    @media (max-width: 599.98px) {
      .wide {
        grid-column: auto;
      }
    }
  `,
})
export class JobSearchPage {
  private readonly router = inject(Router);

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly q = input<string>();
  readonly city = input<string>();
  readonly workMode = input<string>();
  readonly contractType = input<string>();
  readonly experienceLevel = input<string>();
  readonly categoryId = input<string>();
  readonly salaryMin = input<string>();
  readonly sort = input<string>();
  readonly page = input<string>();

  protected readonly workModes = optionsOf(WORK_MODE_LABELS);
  protected readonly contractTypes = optionsOf(CONTRACT_TYPE_LABELS);
  protected readonly levels = optionsOf(EXPERIENCE_LEVEL_LABELS);

  protected readonly categories = httpResource<Category[]>(() => '/api/v1/categories');

  private readonly filters = computed(() => ({
    q: this.q(),
    city: this.city(),
    workMode: this.workMode(),
    contractType: this.contractType(),
    experienceLevel: this.experienceLevel(),
    categoryId: this.categoryId(),
    salaryMin: this.salaryMin(),
    sort: this.sort(),
    page: this.page(),
  }));

  /** Se recharge seule à chaque changement de l'URL ; annule la requête précédente. */
  protected readonly results = httpResource<Page<JobCardModel>>(() => ({
    url: '/api/v1/jobs',
    params: searchParams(this.filters()),
  }));
  protected readonly pageData = computed(() =>
    this.results.hasValue() ? this.results.value() : undefined,
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    q: [''],
    city: [''],
    categoryId: [''],
    contractType: [[] as string[]],
    workMode: [[] as string[]],
    experienceLevel: [[] as string[]],
    salaryMin: [''],
    sort: [''],
  });

  constructor() {
    // Le formulaire reflète l'URL (lien partagé, bouton Précédent)
    effect(() => {
      const filters = this.filters();
      this.form.reset(
        {
          q: filters.q ?? '',
          city: filters.city ?? '',
          categoryId: filters.categoryId ?? '',
          contractType: split(filters.contractType),
          workMode: split(filters.workMode),
          experienceLevel: split(filters.experienceLevel),
          salaryMin: filters.salaryMin ?? '',
          sort: filters.sort ?? '',
        },
        { emitEvent: false },
      );
    });
  }

  protected apply(): void {
    const value = this.form.getRawValue();
    void this.router.navigate([], {
      queryParams: {
        q: value.q.trim() || null,
        city: value.city.trim() || null,
        categoryId: value.categoryId || null,
        contractType: value.contractType.join(',') || null,
        workMode: value.workMode.join(',') || null,
        experienceLevel: value.experienceLevel.join(',') || null,
        salaryMin: String(value.salaryMin ?? '').trim() || null,
        sort: value.sort || null,
        page: null, // un nouveau filtre ramène à la première page
      },
    });
  }

  protected reset(): void {
    void this.router.navigate([], { queryParams: {} });
  }

  protected goTo(event: PageEvent): void {
    void this.router.navigate([], {
      queryParams: { page: event.pageIndex || null },
      queryParamsHandling: 'merge',
    });
    window.scrollTo({ top: 0 });
  }
}

function split(value: string | undefined): string[] {
  return value ? value.split(',').filter(Boolean) : [];
}

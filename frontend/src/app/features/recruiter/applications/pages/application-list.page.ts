import { ChangeDetectionStrategy, Component, computed, effect, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
} from '../../../../shared/models/application';
import { Page } from '../../../../shared/models/job';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import { SkillAutocomplete } from '../../../../shared/ui/skill-autocomplete/skill-autocomplete';
import { SkillOption } from '../../../../shared/ui/skill-autocomplete/skill-search';
import { RecruiterJobSummary } from '../../jobs/data-access/recruiter-jobs.models';
import { ApplicationStatusChip } from '../components/application-status';
import {
  ApplicationFilters,
  RecruiterApplicationItem,
  applicationParams,
} from '../data-access/recruiter-applications.models';

/**
 * US-30 : candidatures de l'entreprise, les plus récentes d'abord. Les filtres vivent dans l'URL
 * (lien partageable, bouton Précédent) ; changer un filtre ramène à la première page.
 */
@Component({
  selector: 'app-application-list-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    EmptyState,
    SkillAutocomplete,
    ApplicationStatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Candidatures</h1>

    <form class="filters" [formGroup]="form" (ngSubmit)="apply()" role="search">
      <mat-form-field>
        <mat-label i18n>Offre</mat-label>
        <mat-select formControlName="jobOfferId">
          <mat-option value="" i18n>Toutes les offres</mat-option>
          @for (job of jobOptions(); track job.id) {
            <mat-option [value]="job.id">{{ job.title }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Statut</mat-label>
        <mat-select formControlName="status" multiple>
          @for (status of statuses; track status) {
            <mat-option [value]="status">{{ statusLabel(status) }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Nom du candidat</mat-label>
        <input matInput formControlName="candidateName" maxlength="100" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Reçue à partir du</mat-label>
        <input matInput type="date" formControlName="submittedFrom" />
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Jusqu'au</mat-label>
        <input matInput type="date" formControlName="submittedTo" />
      </mat-form-field>
      <div class="skill">
        @if (form.controls.skillId.value) {
          <mat-chip-set aria-label="Compétence filtrée" i18n-aria-label>
            <mat-chip (removed)="clearSkill()">
              {{ form.controls.skillName.value }}
              <button matChipRemove aria-label="Retirer le filtre de compétence" i18n-aria-label>
                <mat-icon>cancel</mat-icon>
              </button>
            </mat-chip>
          </mat-chip-set>
        } @else {
          <app-skill-autocomplete
            label="Compétence"
            i18n-label
            [allowCreate]="false"
            (selected)="chooseSkill($event)"
          />
        }
      </div>
      <div class="actions">
        <button mat-button type="button" (click)="reset()" i18n>Effacer</button>
        <button mat-flat-button type="submit" i18n>Filtrer</button>
      </div>
    </form>

    @if (applications.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (applications.error()) {
      <div class="state" role="alert">
        <p i18n>Les candidatures n'ont pas pu être chargées.</p>
        <button mat-flat-button type="button" (click)="applications.reload()" i18n>
          Réessayer
        </button>
      </div>
    } @else if (pageData(); as page) {
      <p class="count" aria-live="polite" i18n>{{ page.totalElements }} candidature(s)</p>
      @if (page.content.length) {
        <table mat-table [dataSource]="page.content" class="table">
          <ng-container matColumnDef="candidate">
            <th mat-header-cell *matHeaderCellDef i18n>Candidat</th>
            <td mat-cell *matCellDef="let item">
              <a [routerLink]="['/recruiter/applications', item.id]">{{
                item.candidate.fullName
              }}</a>
              <div class="sub">{{ item.candidate.headline }}</div>
            </td>
          </ng-container>
          <ng-container matColumnDef="job">
            <th mat-header-cell *matHeaderCellDef i18n>Offre</th>
            <td mat-cell *matCellDef="let item">{{ item.job.title }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef i18n>Statut</th>
            <td mat-cell *matCellDef="let item">
              <app-application-status [status]="item.status" />
            </td>
          </ng-container>
          <ng-container matColumnDef="submittedAt">
            <th mat-header-cell *matHeaderCellDef i18n>Reçue le</th>
            <td mat-cell *matCellDef="let item">{{ item.submittedAt | date: 'd MMM y' }}</td>
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
            aria-label="Pages de candidatures"
            i18n-aria-label
          />
        }
      } @else {
        <app-empty-state
          icon="inbox"
          title="Aucune candidature"
          i18n-title
          message="Aucune candidature ne correspond à ces filtres pour l'instant."
          i18n-message
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
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 0 12px;
    }
    .skill {
      min-width: 0;
    }
    .actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 8px;
    }
    .count,
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .sub {
      font: var(--mat-sys-body-small);
    }
    .table {
      width: 100%;
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
export class ApplicationListPage {
  private readonly router = inject(Router);

  // Paramètres de requête liés aux input() par withComponentInputBinding
  readonly jobOfferId = input<string>();
  readonly status = input<string>();
  readonly skillId = input<string>();
  readonly skillName = input<string>();
  readonly candidateName = input<string>();
  readonly submittedFrom = input<string>();
  readonly submittedTo = input<string>();
  readonly page = input<string>();

  protected readonly statuses = APPLICATION_STATUSES;
  protected readonly columns = ['candidate', 'job', 'status', 'submittedAt'];

  /** Offres de l'entreprise pour le filtre (archives comprises : leurs candidatures restent consultables). */
  protected readonly jobs = httpResource<Page<RecruiterJobSummary>>(() => ({
    url: '/api/v1/company/jobs',
    params: { size: 100 },
  }));
  protected readonly jobOptions = computed(() =>
    this.jobs.hasValue() ? this.jobs.value().content : [],
  );

  private readonly filters = computed<ApplicationFilters>(() => ({
    jobOfferId: this.jobOfferId(),
    status: this.status(),
    skillId: this.skillId(),
    skillName: this.skillName(),
    candidateName: this.candidateName(),
    submittedFrom: this.submittedFrom(),
    submittedTo: this.submittedTo(),
    page: this.page(),
  }));

  protected readonly applications = httpResource<Page<RecruiterApplicationItem>>(() => ({
    url: '/api/v1/applications',
    params: applicationParams(this.filters()),
  }));
  protected readonly pageData = computed(() =>
    this.applications.hasValue() ? this.applications.value() : undefined,
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    jobOfferId: [''],
    status: [[] as ApplicationStatus[]],
    skillId: [''],
    skillName: [''],
    candidateName: [''],
    submittedFrom: [''],
    submittedTo: [''],
  });

  constructor() {
    // Le formulaire reflète l'URL (lien partagé, bouton Précédent)
    effect(() => {
      const filters = this.filters();
      this.form.reset(
        {
          jobOfferId: filters.jobOfferId ?? '',
          status: (filters.status ?? '').split(',').filter(Boolean) as ApplicationStatus[],
          skillId: filters.skillId ?? '',
          skillName: filters.skillName ?? '',
          candidateName: filters.candidateName ?? '',
          submittedFrom: filters.submittedFrom ?? '',
          submittedTo: filters.submittedTo ?? '',
        },
        { emitEvent: false },
      );
    });
  }

  protected statusLabel(status: ApplicationStatus): string {
    return APPLICATION_STATUS_LABELS[status];
  }

  protected chooseSkill(skill: SkillOption): void {
    this.form.patchValue({ skillId: skill.id, skillName: skill.name });
  }

  protected clearSkill(): void {
    this.form.patchValue({ skillId: '', skillName: '' });
  }

  protected apply(): void {
    const value = this.form.getRawValue();
    void this.router.navigate([], {
      queryParams: {
        jobOfferId: value.jobOfferId || null,
        status: value.status.join(',') || null,
        skillId: value.skillId || null,
        skillName: value.skillId ? value.skillName : null,
        candidateName: value.candidateName.trim() || null,
        submittedFrom: value.submittedFrom || null,
        submittedTo: value.submittedTo || null,
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
}

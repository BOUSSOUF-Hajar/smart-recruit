import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthStore } from '../../../../core/auth/auth.store';
import { isApiError } from '../../../../core/errors/api-error';
import {
  CONTRACT_TYPE_LABELS,
  EXPERIENCE_LEVEL_LABELS,
  SKILL_LEVEL_LABELS,
  WORK_MODE_LABELS,
  formatSalary,
} from '../../../../shared/models/job';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import {
  ApplyDialog,
  ApplyDialogData,
  ApplyDialogResult,
} from '../../../candidate/applications/components/apply-dialog';
import { JobDetail } from '../data-access/public-jobs.models';

/**
 * US-26 : détail public d'une offre ouverte ; 404 pour un brouillon, une offre clôturée ou expirée.
 * US-27 : un candidat connecté y postule ; s'il l'a déjà fait, un lien mène à sa candidature.
 */
@Component({
  selector: 'app-job-detail-page',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatProgressBarModule, EmptyState],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a mat-button routerLink="/jobs" class="back">
      <mat-icon>arrow_back</mat-icon>
      <span i18n>Toutes les offres</span>
    </a>

    @if (job.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (detail(); as job) {
      <article>
        <header class="header">
          <h1>{{ job.title }}</h1>
          <p class="company">{{ job.company.name }} · {{ job.location.city }}</p>
          <ul class="facts">
            <li>{{ contract(job) }}</li>
            <li>{{ workMode(job) }}</li>
            <li>{{ level(job) }}</li>
            @if (job.minYearsExperience > 0) {
              <li i18n>{{ job.minYearsExperience }} an(s) d'expérience minimum</li>
            }
            @if (salary(job); as salary) {
              <li>{{ salary }}</li>
            }
            <li>{{ job.category.name }}</li>
          </ul>
          <p class="meta" i18n>
            Publiée le {{ job.publishedAt | date: 'd MMMM y' }} · candidatures jusqu'au
            {{ job.expiresAt | date: 'd MMMM y' }}
          </p>

          <div class="apply">
            @switch (applyState(job)) {
              @case ('anonymous') {
                <a
                  mat-flat-button
                  [routerLink]="['/login']"
                  [queryParams]="{ returnUrl: '/jobs/' + job.id }"
                  i18n
                  >Se connecter pour postuler</a
                >
              }
              @case ('applied') {
                <p class="applied" i18n>Vous avez déjà postulé à cette offre.</p>
                @if (job.myApplicationId) {
                  <a
                    mat-stroked-button
                    [routerLink]="['/candidate/applications', job.myApplicationId]"
                    i18n
                    >Suivre ma candidature</a
                  >
                }
              }
              @case ('candidate') {
                <button mat-flat-button type="button" (click)="openApply(job)" i18n>
                  Postuler
                </button>
              }
            }
          </div>
        </header>

        <section>
          <h2 i18n>Description</h2>
          <p class="description">{{ job.description }}</p>
        </section>

        <section>
          <h2 i18n>Compétences requises</h2>
          <ul class="skills">
            @for (skill of job.requiredSkills; track skill.id) {
              <li>
                <strong>{{ skill.name }}</strong> · {{ skillLevel(skill.minLevel) }}
              </li>
            }
          </ul>
          @if (job.preferredSkills.length) {
            <h2 i18n>Compétences appréciées</h2>
            <ul class="skills">
              @for (skill of job.preferredSkills; track skill.id) {
                <li>{{ skill.name }}</li>
              }
            </ul>
          }
        </section>
      </article>
    } @else if (notFound()) {
      <app-empty-state
        icon="work_off"
        title="Cette offre n'est plus disponible"
        i18n-title
        message="Elle a peut-être été pourvue ou retirée."
        i18n-message
      >
        <a mat-flat-button routerLink="/jobs" i18n>Voir les offres ouvertes</a>
      </app-empty-state>
    } @else if (job.error()) {
      <p role="alert" i18n>L'offre n'a pas pu être chargée.</p>
    }
  `,
  styles: `
    .back {
      margin-bottom: 8px;
    }
    .header {
      margin-bottom: 24px;
    }
    h1 {
      margin: 0;
      font: var(--mat-sys-headline-small);
    }
    h2 {
      margin: 24px 0 8px;
      font: var(--mat-sys-title-medium);
    }
    .company,
    .meta,
    .hint {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .facts {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      margin: 12px 0 0;
      padding: 0;
      list-style: none;
    }
    .apply {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      margin-top: 16px;
    }
    .applied {
      margin: 0;
      font-weight: 500;
    }
    .description {
      white-space: pre-line;
    }
    .skills {
      padding-left: 20px;
    }
  `,
})
export class JobDetailPage {
  private readonly store = inject(AuthStore);
  private readonly dialog = inject(MatDialog);

  /** Paramètre de route `:id`. */
  readonly id = input.required<string>();

  protected readonly job = httpResource<JobDetail>(() => `/api/v1/jobs/${this.id()}`);
  protected readonly detail = computed(() => (this.job.hasValue() ? this.job.value() : undefined));
  protected readonly notFound = computed(() => {
    const error = this.job.error();
    return isApiError(error) && error.status === 404;
  });

  protected contract(job: JobDetail): string {
    return CONTRACT_TYPE_LABELS[job.contractType];
  }

  protected workMode(job: JobDetail): string {
    return WORK_MODE_LABELS[job.workMode];
  }

  protected level(job: JobDetail): string {
    return EXPERIENCE_LEVEL_LABELS[job.experienceLevel];
  }

  protected salary(job: JobDetail): string | null {
    return formatSalary(job.salary);
  }

  protected skillLevel(level: keyof typeof SKILL_LEVEL_LABELS): string {
    return $localize`niveau minimum ${SKILL_LEVEL_LABELS[level]}:level:`;
  }

  /** US-27 : récapitulatif puis confirmation ; la page se recharge pour afficher le lien de suivi. */
  protected openApply(job: JobDetail): void {
    this.dialog
      .open<ApplyDialog, ApplyDialogData, ApplyDialogResult>(ApplyDialog, {
        data: { jobId: job.id, jobTitle: job.title, companyName: job.company.name },
        width: '520px',
      })
      .afterClosed()
      .subscribe((result) => {
        if (result) {
          this.job.reload();
        }
      });
  }

  /** Visiteur : se connecter ; candidat : postuler ou rappel de sa candidature ; personnel : rien. */
  protected applyState(job: JobDetail): 'anonymous' | 'candidate' | 'applied' | 'none' {
    if (!this.store.isAuthenticated()) {
      return 'anonymous';
    }
    if (this.store.role() !== 'CANDIDATE') {
      return 'none';
    }
    return job.alreadyApplied ? 'applied' : 'candidate';
  }
}

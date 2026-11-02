import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import {
  CONTRACT_TYPE_LABELS,
  EXPERIENCE_LEVEL_LABELS,
  WORK_MODE_LABELS,
  formatSalary,
} from '../../../../shared/models/job';
import { JobCard as JobCardModel } from '../data-access/public-jobs.models';

/** Une offre dans la liste de résultats. */
@Component({
  selector: 'app-job-card',
  imports: [DatePipe, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card">
      <h2 class="title">
        <a [routerLink]="['/jobs', job().id]">{{ job().title }}</a>
      </h2>
      <p class="company">{{ job().company.name }} · {{ job().location.city }}</p>
      <ul class="facts">
        <li>{{ contract() }}</li>
        <li>{{ workMode() }}</li>
        <li>{{ level() }}</li>
        @if (salary(); as salary) {
          <li>{{ salary }}</li>
        }
      </ul>
      @if (job().topSkills.length) {
        <p class="skills">{{ job().topSkills.join(' · ') }}</p>
      }
      <p class="meta" i18n>Publiée le {{ job().publishedAt | date: 'd MMMM y' }}</p>
    </article>
  `,
  styles: `
    .card {
      padding: 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .title {
      margin: 0;
      font: var(--mat-sys-title-medium);
    }
    .title a {
      color: var(--mat-sys-primary);
      text-decoration: none;
    }
    .title a:hover,
    .title a:focus-visible {
      text-decoration: underline;
    }
    .company,
    .meta {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .facts {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 16px;
      margin: 8px 0 0;
      padding: 0;
      list-style: none;
    }
    .skills {
      margin: 8px 0 0;
      font: var(--mat-sys-body-small);
    }
  `,
})
export class JobCard {
  readonly job = input.required<JobCardModel>();

  protected readonly contract = computed(() => CONTRACT_TYPE_LABELS[this.job().contractType]);
  protected readonly workMode = computed(() => WORK_MODE_LABELS[this.job().workMode]);
  protected readonly level = computed(() => EXPERIENCE_LEVEL_LABELS[this.job().experienceLevel]);
  protected readonly salary = computed(() => formatSalary(this.job().salary));
}

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { JOB_STATUS_LABELS, JobStatus } from '../../../../shared/models/job';
import { StatusChip, StatusTone } from '../../../../shared/ui/status-chip/status-chip';

const TONES: Record<JobStatus, StatusTone> = {
  DRAFT: 'neutral',
  PUBLISHED: 'success',
  CLOSED: 'warning',
  ARCHIVED: 'neutral',
};

/** Statut d'une offre, avec la mention « expirée » quand une offre publiée a dépassé sa date. */
@Component({
  selector: 'app-job-status',
  imports: [StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-status-chip [label]="label()" [tone]="tone()" />
    @if (expired()) {
      <app-status-chip label="Expirée" i18n-label tone="danger" />
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      gap: 4px;
    }
  `,
})
export class JobStatusChip {
  readonly status = input.required<JobStatus>();
  readonly expired = input(false);

  protected readonly label = computed(() => JOB_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => TONES[this.status()]);
}

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  APPLICATION_STATUS_LABELS,
  APPLICATION_STATUS_TONES,
  ApplicationStatus,
} from '../../../../shared/models/application';
import { StatusChip } from '../../../../shared/ui/status-chip/status-chip';

/** Statut interne d'une candidature (vue recruteur). */
@Component({
  selector: 'app-application-status',
  imports: [StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-status-chip [label]="label()" [tone]="tone()" />`,
})
export class ApplicationStatusChip {
  readonly status = input.required<ApplicationStatus>();
  protected readonly label = computed(() => APPLICATION_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => APPLICATION_STATUS_TONES[this.status()]);
}

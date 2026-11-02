import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import {
  CANDIDATE_STATUS_LABELS,
  CANDIDATE_STATUS_TONES,
  CandidateStatus,
} from '../../../../shared/models/application';
import { StatusChip } from '../../../../shared/ui/status-chip/status-chip';

/** Statut simplifié d'une candidature, tel que le candidat le voit. */
@Component({
  selector: 'app-candidate-status',
  imports: [StatusChip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-status-chip [label]="label()" [tone]="tone()" />`,
})
export class CandidateStatusChip {
  readonly status = input.required<CandidateStatus>();
  protected readonly label = computed(() => CANDIDATE_STATUS_LABELS[this.status()]);
  protected readonly tone = computed(() => CANDIDATE_STATUS_TONES[this.status()]);
}

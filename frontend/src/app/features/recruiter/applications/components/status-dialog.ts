import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import {
  APPLICATION_ACTION_LABELS,
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  REJECTION_REASON_LABELS,
  RejectionReason,
  isFinalStatus,
} from '../../../../shared/models/application';
import { optionsOf } from '../../../../shared/models/job';
import { ChangeStatusRequest, Dossier } from '../data-access/recruiter-applications.models';

/**
 * US-32, US-33 : seules les transitions envoyées par le serveur sont proposées. Un rejet exige un
 * motif ; le commentaire reste interne (jamais montré au candidat).
 */
@Component({
  selector: 'app-status-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Faire avancer la candidature</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="content">
        <p class="current" i18n>Statut actuel : {{ currentLabel }}</p>
        <mat-radio-group
          formControlName="targetStatus"
          class="choices"
          aria-label="Nouveau statut"
          i18n-aria-label
        >
          @for (status of data.allowedTransitions; track status) {
            <mat-radio-button [value]="status">{{ actionLabel(status) }}</mat-radio-button>
          }
        </mat-radio-group>
        @if (offerHidden) {
          <p class="hint" i18n>
            La proposition sera possible après au moins un entretien terminé (onglet Entretiens).
          </p>
        }

        @if (rejecting()) {
          <mat-form-field>
            <mat-label i18n>Motif du rejet</mat-label>
            <mat-select formControlName="rejectionReason" required>
              @for (reason of reasons; track reason.value) {
                <mat-option [value]="reason.value">{{ reason.label }}</mat-option>
              }
            </mat-select>
            <mat-error i18n>Un rejet exige un motif.</mat-error>
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label i18n>Commentaire interne (facultatif)</mat-label>
          <textarea matInput formControlName="comment" rows="3" maxlength="1000"></textarea>
          <mat-hint i18n>Visible des recruteurs uniquement.</mat-hint>
        </mat-form-field>
        @if (final()) {
          <p class="warning" role="note" i18n>
            Ce choix est définitif : la candidature ne pourra plus évoluer.
          </p>
        }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="!form.controls.targetStatus.value" i18n>
          Valider
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      display: flex;
      flex-direction: column;
      min-width: min(440px, 80vw);
    }
    .current {
      margin-top: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .choices {
      display: flex;
      flex-direction: column;
      gap: 4px;
      margin-bottom: 16px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .warning {
      color: var(--mat-sys-error);
    }
  `,
})
export class StatusDialog {
  private readonly dialogRef =
    inject<MatDialogRef<StatusDialog, ChangeStatusRequest>>(MatDialogRef);
  protected readonly data = inject<Dossier>(MAT_DIALOG_DATA);

  protected readonly reasons = optionsOf(REJECTION_REASON_LABELS);
  protected readonly currentLabel = APPLICATION_STATUS_LABELS[this.data.status];
  /** INTERVIEW sans OFFER proposé : on explique pourquoi (RG-33). */
  protected readonly offerHidden =
    this.data.status === 'INTERVIEW' && !this.data.allowedTransitions.includes('OFFER');

  protected readonly form = inject(FormBuilder).group({
    targetStatus: [
      (this.data.allowedTransitions.length === 1
        ? this.data.allowedTransitions[0]
        : null) as ApplicationStatus | null,
      Validators.required,
    ],
    rejectionReason: [null as RejectionReason | null],
    comment: ['', Validators.maxLength(1000)],
  });

  private readonly target = toSignal(this.form.controls.targetStatus.valueChanges, {
    initialValue: this.form.controls.targetStatus.value,
  });
  protected readonly rejecting = computed(() => this.target() === 'REJECTED');
  protected readonly final = computed(() => {
    const target = this.target();
    return target !== null && isFinalStatus(target);
  });

  protected actionLabel(status: ApplicationStatus): string {
    return APPLICATION_ACTION_LABELS[status];
  }

  protected submit(): void {
    const { targetStatus, rejectionReason, comment } = this.form.getRawValue();
    if (!targetStatus) {
      return;
    }
    if (targetStatus === 'REJECTED' && !rejectionReason) {
      this.form.controls.rejectionReason.setErrors({ required: true });
      this.form.controls.rejectionReason.markAsTouched();
      return;
    }
    this.dialogRef.close({
      targetStatus,
      rejectionReason: targetStatus === 'REJECTED' ? rejectionReason : null,
      comment: comment?.trim() || null,
      version: this.data.version,
    });
  }
}

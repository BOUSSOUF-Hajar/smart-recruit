import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export type CancelDialogResult = { reason: string | null } | undefined;

/** US-36 : annulation définitive, motif facultatif (interne). */
@Component({
  selector: 'app-cancel-interview-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Annuler l'entretien ?</h2>
    <mat-dialog-content>
      <p i18n>L'annulation est définitive ; le candidat verra l'entretien comme annulé.</p>
      <mat-form-field class="reason">
        <mat-label i18n>Motif (facultatif, interne)</mat-label>
        <textarea matInput [formControl]="reason" rows="2" maxlength="500"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close i18n>Garder l'entretien</button>
      <button mat-flat-button type="button" (click)="confirm()" [disabled]="reason.invalid" i18n>
        Annuler l'entretien
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .reason {
      width: 100%;
    }
  `,
})
export class CancelInterviewDialog {
  private readonly dialogRef =
    inject<MatDialogRef<CancelInterviewDialog, CancelDialogResult>>(MatDialogRef);
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(500)],
  });

  protected confirm(): void {
    this.dialogRef.close({ reason: this.reason.value.trim() || null });
  }
}

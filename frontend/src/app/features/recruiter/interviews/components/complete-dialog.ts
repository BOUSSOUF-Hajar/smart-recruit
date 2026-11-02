import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { FEEDBACK_RATING_LABELS, FeedbackRating } from '../../../../shared/models/interview';
import { optionsOf } from '../../../../shared/models/job';

export type CompleteDialogResult = { rating: FeedbackRating; comment: string | null } | undefined;

/** US-37 : avis interne à la clôture ; il n'est jamais montré au candidat (RG-23). */
@Component({
  selector: 'app-complete-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatRadioModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Clôturer l'entretien</h2>
    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <mat-dialog-content class="content">
        <mat-radio-group formControlName="rating" class="ratings" aria-label="Avis" i18n-aria-label>
          @for (option of ratings; track option.value) {
            <mat-radio-button [value]="option.value">{{ option.label }}</mat-radio-button>
          }
        </mat-radio-group>
        <mat-form-field>
          <mat-label i18n>Commentaire</mat-label>
          <textarea matInput formControlName="comment" rows="4" maxlength="2000"></textarea>
          <mat-hint i18n>Visible des recruteurs uniquement.</mat-hint>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="form.invalid" i18n>Clôturer</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      display: flex;
      flex-direction: column;
      min-width: min(420px, 80vw);
    }
    .ratings {
      display: flex;
      gap: 16px;
      margin-bottom: 16px;
    }
  `,
})
export class CompleteDialog {
  private readonly dialogRef =
    inject<MatDialogRef<CompleteDialog, CompleteDialogResult>>(MatDialogRef);
  protected readonly ratings = optionsOf(FEEDBACK_RATING_LABELS);
  protected readonly form = inject(FormBuilder).group({
    rating: [null as FeedbackRating | null, Validators.required],
    comment: ['', Validators.maxLength(2000)],
  });

  protected submit(): void {
    const { rating, comment } = this.form.getRawValue();
    if (rating) {
      this.dialogRef.close({ rating, comment: comment?.trim() || null });
    }
  }
}

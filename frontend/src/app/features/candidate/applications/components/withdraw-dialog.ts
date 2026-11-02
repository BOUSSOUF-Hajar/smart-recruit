import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

/** Résultat : le commentaire (éventuellement vide) si le candidat confirme, `undefined` sinon. */
export type WithdrawDialogResult = { comment: string | null } | undefined;

/**
 * US-29 : confirmation du retrait, avec un commentaire facultatif lu par le recruteur. Le retrait est
 * définitif et interdit toute nouvelle candidature à la même offre (RG-10) : on le dit avant.
 */
@Component({
  selector: 'app-withdraw-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Retirer ma candidature</h2>
    <mat-dialog-content>
      <p i18n>
        Votre candidature à « {{ jobTitle }} » sera retirée définitivement. Vous ne pourrez plus
        postuler à cette offre.
      </p>
      <mat-form-field class="comment">
        <mat-label i18n>Message au recruteur (facultatif)</mat-label>
        <textarea matInput [formControl]="comment" rows="3" maxlength="1000"></textarea>
        <mat-hint align="end">{{ comment.value.length }} / 1000</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
      <button mat-flat-button type="button" (click)="confirm()" [disabled]="comment.invalid" i18n>
        Retirer
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .comment {
      width: 100%;
    }
  `,
})
export class WithdrawDialog {
  private readonly dialogRef =
    inject<MatDialogRef<WithdrawDialog, WithdrawDialogResult>>(MatDialogRef);
  protected readonly jobTitle = inject<string>(MAT_DIALOG_DATA);
  protected readonly comment = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(1000)],
  });

  protected confirm(): void {
    this.dialogRef.close({ comment: this.comment.value.trim() || null });
  }
}

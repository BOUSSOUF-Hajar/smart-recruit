import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface ExpiryDialogData {
  title: string;
  confirmLabel: string;
}

const MAX_DAYS = 180;

/** Choix de la date d'expiration pour publier ou rouvrir une offre (entre aujourd'hui et +180 jours, RG-19). */
@Component({
  selector: 'app-expiry-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <p i18n>Les candidatures seront acceptées jusqu'à la fin de cette journée.</p>
      <mat-form-field>
        <mat-label i18n>Date d'expiration</mat-label>
        <input matInput type="date" [formControl]="date" [min]="today" [max]="latest" required />
        <mat-hint i18n>180 jours au plus</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
      <button mat-flat-button type="button" [disabled]="date.invalid" (click)="confirm()">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
})
export class ExpiryDialog {
  protected readonly data = inject<ExpiryDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ExpiryDialog, string>>(MatDialogRef);

  protected readonly today = isoDate(0);
  protected readonly latest = isoDate(MAX_DAYS);
  protected readonly date = new FormControl(isoDate(30), {
    nonNullable: true,
    validators: Validators.required,
  });

  protected confirm(): void {
    this.dialogRef.close(this.date.value);
  }
}

/** Date locale `YYYY-MM-DD`, décalée de `days` jours. */
export function isoDate(days: number, from = new Date()): string {
  const date = new Date(from.getFullYear(), from.getMonth(), from.getDate() + days);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';
import { fieldErrorMessage } from '../../../shared/forms/field-error';

export interface NameDialogData {
  title: string;
  label: string;
  value: string;
  confirmLabel: string;
}

/** Saisie d'un nom (catégorie, compétence) : création ou renommage, 2 à 80 caractères. */
@Component({
  selector: 'app-name-dialog',
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
    <form (ngSubmit)="save()" novalidate>
      <mat-dialog-content>
        <mat-form-field class="field">
          <mat-label>{{ data.label }}</mat-label>
          <input matInput [formControl]="name" required cdkFocusInitial />
          <mat-error>{{ error() }}</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
        <button mat-flat-button type="submit">{{ data.confirmLabel }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .field {
      width: min(380px, 75vw);
    }
  `,
})
export class NameDialog {
  protected readonly data = inject<NameDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<NameDialog, string>>(MatDialogRef);

  protected readonly name = new FormControl(this.data.value, {
    nonNullable: true,
    validators: [Validators.required, Validators.minLength(2), Validators.maxLength(80)],
  });

  protected error(): string | null {
    return fieldErrorMessage(this.name.errors);
  }

  protected save(): void {
    const value = this.name.value.trim();
    if (this.name.invalid || value.length < 2) {
      this.name.markAsTouched();
      return;
    }
    this.dialogRef.close(value);
  }
}

/** Ouvre la saisie ; émet le nom saisi, ou `undefined` si l'admin annule. */
export function askName(dialog: MatDialog, data: NameDialogData): Observable<string | undefined> {
  return dialog.open<NameDialog, NameDialogData, string>(NameDialog, { data }).afterClosed();
}

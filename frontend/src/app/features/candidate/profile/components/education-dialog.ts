import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';
import { isApiError } from '../../../../core/errors/api-error';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { chronologicalOrder } from '../../../../shared/forms/validators';
import { CandidateProfileApi } from '../data-access/profile.api';
import { Education } from '../data-access/profile.models';

type Field = 'degree' | 'school' | 'fieldOfStudy' | 'startYear' | 'endYear';

/** US-14 : ajout ou modification d'une formation (années seules). */
@Component({
  selector: 'app-education-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      @if (existing) {
        <ng-container i18n>Modifier la formation</ng-container>
      } @else {
        <ng-container i18n>Ajouter une formation</ng-container>
      }
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        <mat-form-field>
          <mat-label i18n>Diplôme</mat-label>
          <input
            matInput
            formControlName="degree"
            required
            placeholder="Diplôme d'ingénieur d'État"
          />
          <mat-error>{{ error('degree') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Établissement</mat-label>
          <input matInput formControlName="school" required />
          <mat-error>{{ error('school') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Domaine d'études</mat-label>
          <input matInput formControlName="fieldOfStudy" />
          <mat-error>{{ error('fieldOfStudy') }}</mat-error>
        </mat-form-field>
        <div class="years">
          <mat-form-field>
            <mat-label i18n>Année de début</mat-label>
            <input matInput formControlName="startYear" type="number" min="1950" max="2100" />
            <mat-error>{{ error('startYear') }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label i18n>Année de fin</mat-label>
            <input matInput formControlName="endYear" type="number" min="1950" max="2100" />
            <mat-hint i18n>Vide si en cours</mat-hint>
            <mat-error>{{ error('endYear') }}</mat-error>
          </mat-form-field>
        </div>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()" i18n>Enregistrer</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      display: flex;
      flex-direction: column;
      min-width: min(480px, 80vw);
    }
    .years {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
  `,
})
export class EducationDialog {
  private readonly api = inject(CandidateProfileApi);
  private readonly dialogRef = inject<MatDialogRef<EducationDialog, Education>>(MatDialogRef);
  protected readonly existing = inject<Education | null>(MAT_DIALOG_DATA);

  protected readonly saving = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      degree: [this.existing?.degree ?? '', [Validators.required, Validators.maxLength(120)]],
      school: [this.existing?.school ?? '', [Validators.required, Validators.maxLength(120)]],
      fieldOfStudy: [this.existing?.fieldOfStudy ?? '', Validators.maxLength(120)],
      startYear: [
        this.existing?.startYear ?? (null as number | null),
        [Validators.min(1950), Validators.max(2100)],
      ],
      endYear: [
        this.existing?.endYear ?? (null as number | null),
        [Validators.min(1950), Validators.max(2100)],
      ],
    },
    { validators: chronologicalOrder('startYear', 'endYear') },
  );

  protected error(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field].errors);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request = {
      degree: value.degree.trim(),
      school: value.school.trim(),
      fieldOfStudy: value.fieldOfStudy.trim() || null,
      startYear: value.startYear,
      endYear: value.endYear,
    };
    const call: Observable<Education> = this.existing
      ? this.api.updateEducation(this.existing.id, request)
      : this.api.addEducation(request);
    this.saving.set(true);
    call.subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: unknown) => {
        this.saving.set(false);
        if (isApiError(error) && error.code === 'VALIDATION_FAILED') {
          applyServerErrors(this.form, error.errors);
        }
      },
    });
  }
}

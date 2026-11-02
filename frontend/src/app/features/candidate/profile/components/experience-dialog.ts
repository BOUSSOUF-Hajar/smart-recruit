import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Observable } from 'rxjs';
import { isApiError } from '../../../../core/errors/api-error';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { chronologicalOrder, notInFuture } from '../../../../shared/forms/validators';
import { CandidateProfileApi } from '../data-access/profile.api';
import { Experience } from '../data-access/profile.models';

type Field = 'jobTitle' | 'companyName' | 'city' | 'startDate' | 'endDate' | 'description';

/** US-14 : ajout ou modification d'une expérience. Se ferme avec l'expérience enregistrée. */
@Component({
  selector: 'app-experience-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title>
      @if (existing) {
        <ng-container i18n>Modifier l'expérience</ng-container>
      } @else {
        <ng-container i18n>Ajouter une expérience</ng-container>
      }
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        <mat-form-field>
          <mat-label i18n>Intitulé du poste</mat-label>
          <input matInput formControlName="jobTitle" required />
          <mat-error>{{ error('jobTitle') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Entreprise</mat-label>
          <input matInput formControlName="companyName" required />
          <mat-error>{{ error('companyName') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Ville</mat-label>
          <input matInput formControlName="city" />
          <mat-error>{{ error('city') }}</mat-error>
        </mat-form-field>
        <div class="dates">
          <mat-form-field>
            <mat-label i18n>Début</mat-label>
            <input matInput formControlName="startDate" type="date" required />
            <mat-error>{{ error('startDate') }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label i18n>Fin</mat-label>
            <input matInput formControlName="endDate" type="date" />
            <mat-error>{{ error('endDate') }}</mat-error>
          </mat-form-field>
        </div>
        <mat-checkbox formControlName="current" i18n>Poste actuel</mat-checkbox>
        <mat-form-field>
          <mat-label i18n>Description</mat-label>
          <textarea matInput formControlName="description" rows="4" maxlength="2000"></textarea>
          <mat-error>{{ error('description') }}</mat-error>
        </mat-form-field>
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
    .dates {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
  `,
})
export class ExperienceDialog {
  private readonly api = inject(CandidateProfileApi);
  private readonly dialogRef = inject<MatDialogRef<ExperienceDialog, Experience>>(MatDialogRef);
  protected readonly existing = inject<Experience | null>(MAT_DIALOG_DATA);

  protected readonly saving = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      jobTitle: [this.existing?.jobTitle ?? '', [Validators.required, Validators.maxLength(120)]],
      companyName: [
        this.existing?.companyName ?? '',
        [Validators.required, Validators.maxLength(120)],
      ],
      city: [this.existing?.city ?? '', Validators.maxLength(80)],
      startDate: [this.existing?.startDate ?? '', [Validators.required, notInFuture]],
      endDate: [this.existing?.endDate ?? ''],
      current: [this.existing?.current ?? false],
      description: [this.existing?.description ?? '', Validators.maxLength(2000)],
    },
    { validators: chronologicalOrder('startDate', 'endDate') },
  );

  constructor() {
    this.syncEndDate(this.form.controls.current.value);
    this.form.controls.current.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((current) => this.syncEndDate(current));
  }

  /** Un poste en cours n'a pas de date de fin : le champ est vidé et désactivé. */
  private syncEndDate(current: boolean): void {
    const endDate = this.form.controls.endDate;
    if (current) {
      endDate.setValue('');
      endDate.disable();
    } else {
      endDate.enable();
    }
  }

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
      jobTitle: value.jobTitle.trim(),
      companyName: value.companyName.trim(),
      city: value.city.trim() || null,
      startDate: value.startDate,
      endDate: value.current || !value.endDate ? null : value.endDate,
      description: value.description.trim() || null,
    };
    const call: Observable<Experience> = this.existing
      ? this.api.updateExperience(this.existing.id, request)
      : this.api.addExperience(request);
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

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { isApiError } from '../../../core/errors/api-error';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../shared/forms/field-error';
import { httpsUrl } from '../../../shared/forms/validators';
import { AdminApi, COMPANY_SIZES, Company } from '../data-access/admin.api';

type Field = 'name' | 'sector' | 'city' | 'countryCode' | 'website';

/**
 * Création ou modification d'une entreprise cliente (US-49) ; nom unique sans tenir compte de la casse.
 * En modification, la version lue part avec la requête : une modification concurrente est signalée.
 */
@Component({
  selector: 'app-company-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (existing) {
      <h2 mat-dialog-title i18n>Modifier l'entreprise</h2>
    } @else {
      <h2 mat-dialog-title i18n>Nouvelle entreprise</h2>
    }
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        @if (formError(); as message) {
          <p class="form-error" role="alert">{{ message }}</p>
        }
        <mat-form-field>
          <mat-label i18n>Nom</mat-label>
          <input matInput formControlName="name" required />
          <mat-error>{{ error('name') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Secteur</mat-label>
          <input matInput formControlName="sector" />
          <mat-error>{{ error('sector') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Effectif</mat-label>
          <mat-select formControlName="sizeRange">
            <mat-option [value]="null" i18n>Non renseigné</mat-option>
            @for (size of sizes; track size) {
              <mat-option [value]="size">{{ size }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <div class="pair">
          <mat-form-field>
            <mat-label i18n>Ville</mat-label>
            <input matInput formControlName="city" />
            <mat-error>{{ error('city') }}</mat-error>
          </mat-form-field>
          <mat-form-field class="short">
            <mat-label i18n>Pays</mat-label>
            <input matInput formControlName="countryCode" maxlength="2" />
            <mat-error>{{ error('countryCode') }}</mat-error>
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label i18n>Site web</mat-label>
          <input matInput formControlName="website" type="url" placeholder="https://" />
          <mat-error>{{ error('website') }}</mat-error>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ submitLabel }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .content {
      display: flex;
      flex-direction: column;
      min-width: min(440px, 80vw);
    }
    .pair {
      display: flex;
      gap: 12px;
    }
    .pair mat-form-field:first-child {
      flex: 1;
    }
    .short {
      width: 96px;
    }
    .form-error {
      color: var(--mat-sys-error);
    }
  `,
})
export class CompanyDialog {
  private readonly api = inject(AdminApi);
  private readonly dialogRef = inject<MatDialogRef<CompanyDialog, Company>>(MatDialogRef);
  /** Entreprise à modifier ; absente pour une création. */
  protected readonly existing = inject<Company | null>(MAT_DIALOG_DATA, { optional: true });
  protected readonly submitLabel = this.existing ? $localize`Enregistrer` : $localize`Créer`;

  protected readonly sizes = COMPANY_SIZES;
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.existing?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    sector: [this.existing?.sector ?? '', Validators.maxLength(80)],
    sizeRange: [this.existing?.sizeRange ?? (null as string | null)],
    city: [this.existing?.city ?? '', Validators.maxLength(80)],
    countryCode: [
      this.existing ? (this.existing.countryCode ?? '') : 'MA',
      Validators.pattern(/^[A-Za-z]{2}$/),
    ],
    website: [this.existing?.website ?? '', [Validators.maxLength(255), httpsUrl]],
  });

  protected error(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field].errors);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    const request = {
      name: value.name.trim(),
      sector: value.sector.trim() || null,
      sizeRange: value.sizeRange,
      city: value.city.trim() || null,
      countryCode: value.countryCode.trim().toUpperCase() || null,
      website: value.website.trim() || null,
    };
    const call = this.existing
      ? this.api.updateCompany(this.existing.id, request, this.existing.version)
      : this.api.createCompany(request);
    call.subscribe({
      next: (company) => this.dialogRef.close(company),
      error: (error: unknown) => {
        this.saving.set(false);
        if (isApiError(error) && error.code === 'RESOURCE_IN_USE') {
          this.form.controls.name.setErrors({
            server: $localize`Une entreprise porte déjà ce nom.`,
          });
        } else if (isApiError(error) && error.code === 'CONCURRENT_MODIFICATION') {
          this.formError.set(
            $localize`Cette entreprise a été modifiée entre-temps : fermez et rouvrez la fiche.`,
          );
        } else if (
          isApiError(error) &&
          error.code === 'VALIDATION_FAILED' &&
          !applyServerErrors(this.form, error.errors)
        ) {
          this.formError.set($localize`Vérifiez les champs du formulaire.`);
        }
      },
    });
  }
}

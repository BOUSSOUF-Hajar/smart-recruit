import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { isApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../shared/forms/field-error';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, AdminUser, Company, ROLE_LABELS } from '../data-access/admin.api';

export interface UserDialogData {
  user: AdminUser;
  companies: Company[];
}

type Field = 'firstName' | 'lastName' | 'companyId' | 'jobTitle';

/**
 * US-48 : nom, entreprise et poste d'un compte. Le rôle s'affiche mais ne se modifie pas (G-3).
 * Changer l'entreprise d'un recruteur ferme ses sessions : l'écran le dit avant l'enregistrement.
 */
@Component({
  selector: 'app-user-dialog',
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
    <h2 mat-dialog-title i18n>Modifier le compte</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        <p class="sub">{{ data.user.email }} · {{ roleLabel }}</p>
        <div class="pair">
          <mat-form-field>
            <mat-label i18n>Prénom</mat-label>
            <input matInput formControlName="firstName" required />
            <mat-error>{{ error('firstName') }}</mat-error>
          </mat-form-field>
          <mat-form-field>
            <mat-label i18n>Nom</mat-label>
            <input matInput formControlName="lastName" required />
            <mat-error>{{ error('lastName') }}</mat-error>
          </mat-form-field>
        </div>
        @if (isRecruiter) {
          <mat-form-field>
            <mat-label i18n>Entreprise</mat-label>
            <mat-select formControlName="companyId" required>
              @for (company of selectableCompanies; track company.id) {
                <mat-option [value]="company.id">{{ company.name }}</mat-option>
              }
            </mat-select>
            <mat-error>{{ error('companyId') }}</mat-error>
          </mat-form-field>
          @if (companyChanged()) {
            <p class="warning" role="note" i18n>
              Changer d'entreprise ferme les sessions de ce recruteur : il devra se reconnecter.
            </p>
          }
          <mat-form-field>
            <mat-label i18n>Poste</mat-label>
            <input matInput formControlName="jobTitle" />
            <mat-error>{{ error('jobTitle') }}</mat-error>
          </mat-form-field>
        }
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
      min-width: min(440px, 80vw);
    }
    .pair {
      display: flex;
      gap: 12px;
    }
    .pair mat-form-field {
      flex: 1;
    }
    .sub {
      margin-top: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .warning {
      margin-top: 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class UserDialog {
  protected readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<UserDialog, AdminUser>>(MatDialogRef);

  protected readonly roleLabel = ROLE_LABELS[this.data.user.role];
  protected readonly isRecruiter = this.data.user.role === 'RECRUITER';
  /** Entreprises actives, plus l'actuelle même si elle est désactivée. */
  protected readonly selectableCompanies = this.data.companies.filter(
    (company) => company.active || company.id === this.data.user.companyId,
  );
  protected readonly saving = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: [this.data.user.firstName, [Validators.required, Validators.maxLength(80)]],
    lastName: [this.data.user.lastName, [Validators.required, Validators.maxLength(80)]],
    companyId: [this.data.user.companyId ?? '', this.isRecruiter ? Validators.required : []],
    jobTitle: [this.data.user.jobTitle ?? '', Validators.maxLength(120)],
  });

  private readonly companyId = toSignal(this.form.controls.companyId.valueChanges, {
    initialValue: this.form.controls.companyId.value,
  });
  protected readonly companyChanged = computed(
    () => this.isRecruiter && this.companyId() !== (this.data.user.companyId ?? ''),
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
    this.saving.set(true);
    this.api
      .updateUser(this.data.user.id, {
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        companyId: this.isRecruiter ? value.companyId : null,
        jobTitle: this.isRecruiter ? value.jobTitle.trim() || null : null,
        version: this.data.user.version,
      })
      .subscribe({
        next: (user) => this.dialogRef.close(user),
        error: (error: unknown) => {
          this.saving.set(false);
          if (!(
            isApiError(error) &&
            error.code === 'VALIDATION_FAILED' &&
            applyServerErrors(this.form, error.errors)
          )) {
            notifyFailure(this.notifications, error);
          }
        },
      });
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { isApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../shared/forms/field-error';
import { TemporaryPasswordDialog } from '../components/temporary-password-dialog';
import { Page } from '../../../shared/models/job';
import { AdminApi, Company, StaffRole } from '../data-access/admin.api';
import { toStaffRequest } from '../data-access/staff-form';

type Field = 'email' | 'firstName' | 'lastName' | 'companyId' | 'jobTitle';

/**
 * US-47 : l'admin crée un compte recruteur (rattaché à une entreprise active) ou administrateur.
 * Le mot de passe temporaire n'apparaît qu'une fois, dans un dialogue ; le formulaire est ensuite vidé.
 */
@Component({
  selector: 'app-create-user-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Créer un compte</h1>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate class="form">
      @if (formError(); as message) {
        <p class="form-error" role="alert">{{ message }}</p>
      }
      <mat-button-toggle-group formControlName="role" aria-label="Rôle" i18n-aria-label>
        <mat-button-toggle value="RECRUITER" i18n>Recruteur</mat-button-toggle>
        <mat-button-toggle value="ADMIN" i18n>Administrateur</mat-button-toggle>
      </mat-button-toggle-group>

      <mat-form-field>
        <mat-label i18n>Email</mat-label>
        <input matInput formControlName="email" type="email" autocomplete="off" required />
        <mat-error>{{ error('email') }}</mat-error>
      </mat-form-field>
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

      @if (isRecruiter()) {
        <mat-form-field>
          <mat-label i18n>Entreprise</mat-label>
          <mat-select formControlName="companyId" required>
            @for (company of activeCompanies(); track company.id) {
              <mat-option [value]="company.id">{{ company.name }}</mat-option>
            }
          </mat-select>
          @if (companies.hasValue() && !activeCompanies().length) {
            <mat-hint i18n>Aucune entreprise active : créez-en une d'abord.</mat-hint>
          }
          <mat-error>{{ error('companyId') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Intitulé du poste</mat-label>
          <input matInput formControlName="jobTitle" />
          <mat-error>{{ error('jobTitle') }}</mat-error>
        </mat-form-field>
      }

      <div class="buttons">
        <a mat-button routerLink="/admin/companies" i18n>Entreprises</a>
        <button mat-flat-button type="submit" [disabled]="saving()" i18n>Créer le compte</button>
      </div>
    </form>
  `,
  styles: `
    .form {
      display: flex;
      flex-direction: column;
      gap: 4px;
      max-width: 560px;
    }
    mat-button-toggle-group {
      align-self: flex-start;
      margin-bottom: 16px;
    }
    .pair {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .pair mat-form-field {
      flex: 1 1 200px;
    }
    .buttons {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
    .form-error {
      color: var(--mat-sys-error);
    }
  `,
})
export class CreateUserPage {
  private readonly api = inject(AdminApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    role: ['RECRUITER' as StaffRole],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    companyId: [null as string | null, Validators.required],
    jobTitle: ['', Validators.maxLength(120)],
  });

  private readonly role = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });
  protected readonly isRecruiter = computed(() => this.role() === 'RECRUITER');

  /** Seules les entreprises actives acceptent de nouveaux recruteurs (le serveur le vérifie aussi). */
  protected readonly companies = httpResource<Page<Company>>(() => ({
    url: '/api/v1/admin/companies',
    params: { active: true, size: 100 },
  }));
  protected readonly activeCompanies = computed(() =>
    this.companies.hasValue() ? this.companies.value().content : [],
  );

  constructor() {
    // L'entreprise n'est obligatoire que pour un recruteur : on désactive les champs sinon.
    this.form.controls.role.valueChanges.subscribe((role) => {
      const { companyId, jobTitle } = this.form.controls;
      if (role === 'RECRUITER') {
        companyId.enable();
        jobTitle.enable();
      } else {
        companyId.disable();
        jobTitle.disable();
      }
    });
  }

  protected error(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field].errors);
  }

  protected save(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api.createStaff(toStaffRequest(this.form.getRawValue())).subscribe({
      next: (created) => {
        this.saving.set(false);
        const role = this.form.controls.role.value;
        this.form.reset({ role });
        this.dialog.open(TemporaryPasswordDialog, { data: created, disableClose: true });
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (!isApiError(error)) {
          return;
        }
        switch (error.code) {
          case 'EMAIL_ALREADY_USED':
            this.form.controls.email.setErrors({
              server: $localize`Un compte existe déjà avec cet email.`,
            });
            break;
          case 'INVALID_STATE':
            this.form.controls.companyId.setErrors({
              server: $localize`Cette entreprise est désactivée.`,
            });
            this.companies.reload();
            break;
          case 'VALIDATION_FAILED':
            if (!applyServerErrors(this.form, error.errors)) {
              this.formError.set($localize`Vérifiez les champs du formulaire.`);
            }
            break;
          default:
            this.notifications.error(error.detail ?? error.title);
        }
      },
    });
  }
}

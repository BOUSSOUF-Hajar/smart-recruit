import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthStore } from '../../../core/auth/auth.store';
import { homeFor } from '../../../core/auth/home-route';
import { isApiError } from '../../../core/errors/api-error';
import { NotificationService } from '../../../core/errors/notification.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../shared/forms/field-error';
import { matchingFields } from '../../../shared/forms/validators';

type Field = 'currentPassword' | 'newPassword' | 'confirmation';

/** US-11 : changement de mot de passe, obligatoire après la création d'un compte par l'admin. */
@Component({
  selector: 'app-change-password-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="form-card">
      <h1 i18n>Changer de mot de passe</h1>
      @if (mandatory()) {
        <p class="hint" i18n>
          Votre compte a été créé avec un mot de passe temporaire : choisissez le vôtre pour
          continuer.
        </p>
      } @else {
        <p class="hint" i18n>Vos autres sessions ouvertes seront fermées.</p>
      }

      @if (formError(); as message) {
        <p class="form-error" role="alert">{{ message }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label i18n>Mot de passe actuel</mat-label>
          <input
            matInput
            formControlName="currentPassword"
            type="password"
            autocomplete="current-password"
            required
          />
          <mat-error>{{ error('currentPassword') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Nouveau mot de passe</mat-label>
          <input
            matInput
            formControlName="newPassword"
            type="password"
            autocomplete="new-password"
            required
          />
          <mat-hint i18n>12 caractères minimum.</mat-hint>
          <mat-error>{{ error('newPassword') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Confirmation</mat-label>
          <input
            matInput
            formControlName="confirmation"
            type="password"
            autocomplete="new-password"
            required
          />
          <mat-error>{{ error('confirmation') }}</mat-error>
        </mat-form-field>

        @if (submitting()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <button mat-flat-button type="submit" [disabled]="submitting()" i18n>Enregistrer</button>
      </form>
    </section>
  `,
  styleUrl: '../../../shared/ui/form-card.scss',
})
export class ChangePasswordPage {
  private readonly auth = inject(AuthService);
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly mandatory = computed(() => this.store.mustChangePassword());
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      currentPassword: ['', [Validators.required, Validators.maxLength(128)]],
      newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
      confirmation: ['', Validators.required],
    },
    { validators: matchingFields('newPassword', 'confirmation') },
  );

  protected error(field: Field): string | null {
    return fieldErrorMessage(this.form.controls[field].errors);
  }

  protected submit(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.notifications.success($localize`Mot de passe modifié.`);
        void this.router.navigateByUrl(homeFor(this.store.role()));
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.showError(error);
      },
    });
  }

  private showError(error: unknown): void {
    if (!isApiError(error)) {
      return;
    }
    if (error.code === 'INVALID_CURRENT_PASSWORD') {
      const control = this.form.controls.currentPassword;
      control.setErrors({ server: $localize`Le mot de passe actuel est incorrect.` });
      control.markAsTouched();
    } else if (error.code === 'VALIDATION_FAILED' && !applyServerErrors(this.form, error.errors)) {
      this.formError.set($localize`Vérifiez les champs du formulaire.`);
    }
  }
}

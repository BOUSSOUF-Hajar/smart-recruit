import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../../core/auth/auth.service';
import { homeFor } from '../../../../core/auth/home-route';
import { isApiError } from '../../../../core/errors/api-error';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';

type Field = 'email' | 'firstName' | 'lastName' | 'password';

/** US-06 : inscription d'un candidat ; le rôle n'est pas demandé, le serveur l'impose. */
@Component({
  selector: 'app-register-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="form-card">
      <h1 i18n>Créer un compte candidat</h1>

      @if (formError(); as message) {
        <p class="form-error" role="alert">{{ message }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label i18n>Prénom</mat-label>
          <input matInput formControlName="firstName" autocomplete="given-name" required />
          <mat-error>{{ error('firstName') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Nom</mat-label>
          <input matInput formControlName="lastName" autocomplete="family-name" required />
          <mat-error>{{ error('lastName') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Email</mat-label>
          <input matInput formControlName="email" type="email" autocomplete="email" required />
          <mat-error>{{ error('email') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Mot de passe</mat-label>
          <input
            matInput
            formControlName="password"
            type="password"
            autocomplete="new-password"
            required
          />
          <mat-hint i18n>12 caractères minimum : une phrase est plus sûre qu'un mot.</mat-hint>
          <mat-error>{{ error('password') }}</mat-error>
        </mat-form-field>

        <mat-checkbox formControlName="privacyAccepted" class="privacy">
          <ng-container i18n
            >J'accepte que mes données soient traitées pour mes candidatures.</ng-container
          >
        </mat-checkbox>
        @if (form.controls.privacyAccepted.touched && form.controls.privacyAccepted.invalid) {
          <p class="checkbox-error" role="alert" i18n>
            Votre accord est nécessaire pour créer un compte.
          </p>
        }

        @if (submitting()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <button mat-flat-button type="submit" [disabled]="submitting()" i18n>
          Créer mon compte
        </button>
      </form>

      <p class="alternative" i18n>Déjà inscrit ? <a routerLink="/login">Se connecter</a></p>
    </section>
  `,
  styleUrl: '../../../../shared/ui/form-card.scss',
  styles: `
    .privacy {
      margin: 12px 0 4px;
    }
    .checkbox-error {
      margin: 0 0 8px;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class RegisterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    privacyAccepted: [false, Validators.requiredTrue],
  });

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
    const value = this.form.getRawValue();
    this.auth
      .register({ ...value, firstName: value.firstName.trim(), lastName: value.lastName.trim() })
      .subscribe({
        next: (response) => void this.router.navigateByUrl(homeFor(response.user.role)),
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
    if (error.code === 'EMAIL_ALREADY_USED') {
      const email = this.form.controls.email;
      email.setErrors({ server: $localize`Un compte existe déjà avec cet email.` });
      email.markAsTouched();
    } else if (error.code === 'VALIDATION_FAILED' && !applyServerErrors(this.form, error.errors)) {
      this.formError.set($localize`Vérifiez les champs du formulaire.`);
    } else if (error.code === 'RATE_LIMITED') {
      this.formError.set(
        $localize`Trop d'inscriptions depuis votre connexion. Réessayez plus tard.`,
      );
    }
  }
}

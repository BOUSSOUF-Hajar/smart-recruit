import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { AuthService } from '../../../../core/auth/auth.service';
import { homeFor, safeReturnUrl } from '../../../../core/auth/home-route';
import { ApiError, isApiError } from '../../../../core/errors/api-error';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';

/** US-07 : connexion, puis retour à la page demandée ou au tableau de bord du rôle. */
@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="form-card">
      <h1 i18n>Connexion</h1>

      @if (formError(); as message) {
        <p class="form-error" role="alert">{{ message }}</p>
      }

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <mat-form-field>
          <mat-label i18n>Email</mat-label>
          <input matInput formControlName="email" type="email" autocomplete="username" required />
          <mat-error>{{ error('email') }}</mat-error>
        </mat-form-field>

        <mat-form-field>
          <mat-label i18n>Mot de passe</mat-label>
          <input
            matInput
            formControlName="password"
            type="password"
            autocomplete="current-password"
            required
          />
          <mat-error>{{ error('password') }}</mat-error>
        </mat-form-field>

        @if (submitting()) {
          <mat-progress-bar mode="indeterminate" />
        }
        <button mat-flat-button type="submit" [disabled]="submitting()" i18n>Se connecter</button>
      </form>

      <p class="alternative" i18n>
        Pas encore de compte ? <a routerLink="/register">Créer un compte candidat</a>
      </p>
    </section>
  `,
  styleUrl: '../../../../shared/ui/form-card.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Page demandée avant la redirection vers la connexion (paramètre de requête). */
  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  protected error(field: 'email' | 'password'): string | null {
    return fieldErrorMessage(this.form.controls[field].errors);
  }

  protected submit(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (response) => {
        const user = response.user;
        const destination = user.mustChangePassword
          ? '/account/password'
          : (safeReturnUrl(this.returnUrl()) ?? homeFor(user.role));
        void this.router.navigateByUrl(destination);
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
    if (error.code === 'AUTH_INVALID_CREDENTIALS') {
      this.formError.set($localize`Email ou mot de passe incorrect.`);
    } else if (error.code === 'RATE_LIMITED') {
      this.formError.set(rateLimitMessage(error));
    } else if (error.code === 'VALIDATION_FAILED' && !applyServerErrors(this.form, error.errors)) {
      this.formError.set($localize`Vérifiez les champs du formulaire.`);
    }
  }
}

function rateLimitMessage(error: ApiError): string {
  const seconds = Number(error.extensions['retryAfterSeconds']);
  if (Number.isFinite(seconds) && seconds > 0) {
    const minutes = Math.ceil(seconds / 60);
    return $localize`Trop de tentatives. Réessayez dans ${minutes}:minutes: minute(s).`;
  }
  return $localize`Trop de tentatives. Réessayez dans quelques minutes.`;
}

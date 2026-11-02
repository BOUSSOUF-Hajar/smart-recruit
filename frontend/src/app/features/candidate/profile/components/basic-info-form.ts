import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { e164Phone } from '../../../../shared/forms/validators';
import { CandidateProfile } from '../data-access/profile.models';
import { ProfileStore } from '../data-access/profile.store';

type Field =
  | 'firstName'
  | 'lastName'
  | 'phone'
  | 'city'
  | 'countryCode'
  | 'headline'
  | 'summary'
  | 'yearsOfExperience';

/**
 * US-13 : informations de base. Volontairement aucun champ âge, photo, sexe, nationalité ou
 * situation familiale : ils n'aident pas à évaluer une candidature et exposent à la discrimination.
 */
@Component({
  selector: 'app-basic-info-form',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (conflict()) {
      <div class="banner" role="alert">
        <span i18n>Votre profil a été modifié ailleurs entre-temps.</span>
        <button mat-button type="button" (click)="reload()" i18n>Recharger</button>
      </div>
    }
    <form [formGroup]="form" (ngSubmit)="save()" novalidate class="grid">
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
      <mat-form-field class="wide">
        <mat-label i18n>Titre professionnel</mat-label>
        <input matInput formControlName="headline" placeholder="Développeuse Java / Angular" />
        <mat-hint i18n>Obligatoire pour postuler.</mat-hint>
        <mat-error>{{ error('headline') }}</mat-error>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Téléphone</mat-label>
        <input
          matInput
          formControlName="phone"
          type="tel"
          autocomplete="tel"
          placeholder="+212600000000"
        />
        <mat-error>{{ error('phone') }}</mat-error>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Années d'expérience</mat-label>
        <input matInput formControlName="yearsOfExperience" type="number" min="0" max="50" />
        <mat-error>{{ error('yearsOfExperience') }}</mat-error>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Ville</mat-label>
        <input matInput formControlName="city" autocomplete="address-level2" />
        <mat-error>{{ error('city') }}</mat-error>
      </mat-form-field>
      <mat-form-field>
        <mat-label i18n>Pays (code à 2 lettres)</mat-label>
        <input matInput formControlName="countryCode" maxlength="2" placeholder="MA" />
        <mat-error>{{ error('countryCode') }}</mat-error>
      </mat-form-field>
      <mat-form-field class="wide">
        <mat-label i18n>Résumé</mat-label>
        <textarea matInput formControlName="summary" rows="5" maxlength="2000"></textarea>
        <mat-hint align="end">{{ form.controls.summary.value.length }} / 2000</mat-hint>
        <mat-error>{{ error('summary') }}</mat-error>
      </mat-form-field>
      <div class="actions wide">
        <button mat-flat-button type="submit" [disabled]="saving()" i18n>Enregistrer</button>
      </div>
    </form>
  `,
  styles: `
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 4px 16px;
    }
    .wide {
      grid-column: 1 / -1;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
    .banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      margin-bottom: 16px;
      padding: 8px 16px;
      border-radius: 8px;
      background: var(--mat-sys-tertiary-container);
    }
  `,
})
export class BasicInfoForm {
  private readonly store = inject(ProfileStore);
  private readonly notifications = inject(NotificationService);

  readonly profile = input.required<CandidateProfile>();

  protected readonly saving = signal(false);
  protected readonly conflict = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    phone: ['', [e164Phone, Validators.maxLength(20)]],
    city: ['', Validators.maxLength(80)],
    countryCode: ['', Validators.pattern(/^[A-Za-z]{2}$/)],
    headline: ['', Validators.maxLength(120)],
    summary: ['', Validators.maxLength(2000)],
    yearsOfExperience: [null as number | null, [Validators.min(0), Validators.max(50)]],
  });

  constructor() {
    // Recopie le profil reçu tant que l'utilisateur n'a rien modifié dans le formulaire
    effect(() => {
      const profile = this.profile();
      if (this.form.pristine) {
        this.form.reset({
          firstName: profile.firstName,
          lastName: profile.lastName,
          phone: profile.phone ?? '',
          city: profile.location?.city ?? '',
          countryCode: profile.location?.countryCode ?? '',
          headline: profile.headline ?? '',
          summary: profile.summary ?? '',
          yearsOfExperience: profile.yearsOfExperience,
        });
      }
    });
  }

  protected error(field: Field): string | null {
    const errors = this.form.controls[field].errors;
    return errors?.['pattern']
      ? $localize`Deux lettres, par exemple MA.`
      : fieldErrorMessage(errors);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const city = value.city.trim() || null;
    const countryCode = value.countryCode.trim().toUpperCase() || null;
    this.saving.set(true);
    this.conflict.set(false);
    this.store
      .saveBasics({
        firstName: value.firstName.trim(),
        lastName: value.lastName.trim(),
        phone: value.phone.trim() || null,
        location: city || countryCode ? { city, countryCode } : null,
        headline: value.headline.trim() || null,
        summary: value.summary.trim() || null,
        yearsOfExperience: value.yearsOfExperience,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.markAsPristine();
          this.notifications.success($localize`Informations enregistrées.`);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          if (isApiError(error) && error.code === 'CONCURRENT_MODIFICATION') {
            this.conflict.set(true);
          } else if (isApiError(error) && error.code === 'VALIDATION_FAILED') {
            applyServerErrors(
              this.form,
              error.errors.map((e) => ({ ...e, field: e.field.replace('location.', '') })),
            );
          }
        },
      });
  }

  protected reload(): void {
    this.form.markAsPristine();
    this.conflict.set(false);
    this.store.reload();
  }
}

import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { uniqueBy } from '../../../../shared/forms/validators';
import { LANGUAGES, LANGUAGE_LEVEL_LABELS } from '../data-access/profile-labels';
import { LanguageLevel, PROFILE_LIMITS, ProfileLanguage } from '../data-access/profile.models';
import { ProfileStore } from '../data-access/profile.store';

type LanguageRow = FormGroup<{
  languageCode: FormControl<string>;
  level: FormControl<LanguageLevel>;
}>;

/** US-16 : langues avec un niveau A1 à C2 ou langue maternelle, sans doublon. */
@Component({
  selector: 'app-languages-editor',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 i18n>Langues</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <ul formArrayName="languages" class="rows">
        @for (row of rows.controls; track row; let i = $index) {
          <li [formGroupName]="i" class="row">
            <mat-form-field class="grow">
              <mat-label i18n>Langue</mat-label>
              <mat-select formControlName="languageCode" required>
                @for (language of options; track language.code) {
                  <mat-option [value]="language.code">{{ language.label }}</mat-option>
                }
              </mat-select>
              <mat-error>{{ error(row.controls.languageCode.errors) }}</mat-error>
            </mat-form-field>
            <mat-form-field class="level">
              <mat-label i18n>Niveau</mat-label>
              <mat-select formControlName="level" required>
                @for (level of levels; track level.value) {
                  <mat-option [value]="level.value">{{ level.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button
              mat-icon-button
              type="button"
              (click)="remove(i)"
              aria-label="Retirer la langue"
              i18n-aria-label
            >
              <mat-icon>close</mat-icon>
            </button>
          </li>
        }
      </ul>
      <div class="actions">
        <button mat-button type="button" (click)="add()" [disabled]="rows.length >= limit">
          <mat-icon>add</mat-icon>
          <span i18n>Ajouter une langue</span>
        </button>
        <button mat-flat-button type="submit" [disabled]="saving() || form.pristine" i18n>
          Enregistrer les langues
        </button>
      </div>
    </form>
  `,
  styleUrl: './list-editor.scss',
})
export class LanguagesEditor {
  private readonly store = inject(ProfileStore);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly languages = input.required<ProfileLanguage[]>();

  protected readonly limit = PROFILE_LIMITS.languages;
  protected readonly options = LANGUAGES;
  protected readonly levels = Object.entries(LANGUAGE_LEVEL_LABELS).map(([value, label]) => ({
    value: value as LanguageLevel,
    label,
  }));
  protected readonly saving = signal(false);

  protected readonly rows = new FormArray<LanguageRow>([], uniqueBy('languageCode'));
  protected readonly form = new FormGroup({ languages: this.rows });

  constructor() {
    effect(() => {
      const languages = this.languages();
      if (this.form.pristine) {
        this.rows.clear({ emitEvent: false });
        languages.forEach((language) => this.rows.push(this.row(language), { emitEvent: false }));
        this.rows.updateValueAndValidity();
        this.form.markAsPristine();
      }
    });
  }

  protected add(): void {
    this.rows.push(this.row({ languageCode: '', level: 'B2' }));
    this.form.markAsDirty();
  }

  protected remove(index: number): void {
    this.rows.removeAt(index);
    this.form.markAsDirty();
  }

  protected error(errors: Record<string, unknown> | null): string | null {
    return fieldErrorMessage(errors);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.store.saveLanguages(this.rows.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.markAsPristine();
        this.notifications.success($localize`Langues enregistrées.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (isApiError(error) && error.code === 'VALIDATION_FAILED') {
          applyServerErrors(this.form, error.errors);
        }
      },
    });
  }

  private row(language: ProfileLanguage): LanguageRow {
    return this.fb.group({
      languageCode: [language.languageCode, Validators.required],
      level: [language.level, Validators.required],
    });
  }
}

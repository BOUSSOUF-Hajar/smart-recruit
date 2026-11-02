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
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { httpsUrl, uniqueBy } from '../../../../shared/forms/validators';
import { LINK_TYPE_LABELS } from '../data-access/profile-labels';
import { LinkType, PROFILE_LIMITS, ProfileLink } from '../data-access/profile.models';
import { ProfileStore } from '../data-access/profile.store';

type LinkRow = FormGroup<{ type: FormControl<LinkType>; url: FormControl<string> }>;

/** US-16 : liens professionnels, en https, 5 au maximum. */
@Component({
  selector: 'app-links-editor',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 i18n>Liens professionnels</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <ul formArrayName="links" class="rows">
        @for (row of rows.controls; track row; let i = $index) {
          <li [formGroupName]="i" class="row">
            <mat-form-field class="level">
              <mat-label i18n>Type</mat-label>
              <mat-select formControlName="type">
                @for (type of types; track type.value) {
                  <mat-option [value]="type.value">{{ type.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field class="grow">
              <mat-label i18n>Adresse</mat-label>
              <input matInput formControlName="url" type="url" placeholder="https://" required />
              <mat-error>{{ error(row.controls.url.errors) }}</mat-error>
            </mat-form-field>
            <button
              mat-icon-button
              type="button"
              (click)="remove(i)"
              aria-label="Retirer le lien"
              i18n-aria-label
            >
              <mat-icon>close</mat-icon>
            </button>
          </li>
        } @empty {
          <li class="hint" i18n>LinkedIn, GitHub ou portfolio : 5 liens au maximum.</li>
        }
      </ul>
      <div class="actions">
        <button mat-button type="button" (click)="add()" [disabled]="rows.length >= limit">
          <mat-icon>add</mat-icon>
          <span i18n>Ajouter un lien</span>
        </button>
        <button mat-flat-button type="submit" [disabled]="saving() || form.pristine" i18n>
          Enregistrer les liens
        </button>
      </div>
    </form>
  `,
  styleUrl: './list-editor.scss',
})
export class LinksEditor {
  private readonly store = inject(ProfileStore);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly links = input.required<ProfileLink[]>();

  protected readonly limit = PROFILE_LIMITS.links;
  protected readonly types = Object.entries(LINK_TYPE_LABELS).map(([value, label]) => ({
    value: value as LinkType,
    label,
  }));
  protected readonly saving = signal(false);

  protected readonly rows = new FormArray<LinkRow>([], uniqueBy('url'));
  protected readonly form = new FormGroup({ links: this.rows });

  constructor() {
    effect(() => {
      const links = this.links();
      if (this.form.pristine) {
        this.rows.clear({ emitEvent: false });
        links.forEach((link) => this.rows.push(this.row(link), { emitEvent: false }));
        this.rows.updateValueAndValidity();
        this.form.markAsPristine();
      }
    });
  }

  protected add(): void {
    if (this.rows.length < this.limit) {
      this.rows.push(this.row({ type: 'LINKEDIN', url: '' }));
      this.form.markAsDirty();
    }
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
    const links = this.rows.getRawValue().map((link) => ({ ...link, url: link.url.trim() }));
    this.store.saveLinks(links).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.markAsPristine();
        this.notifications.success($localize`Liens enregistrés.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (isApiError(error) && error.code === 'VALIDATION_FAILED') {
          applyServerErrors(this.form, error.errors);
        }
      },
    });
  }

  private row(link: ProfileLink): LinkRow {
    return this.fb.group({
      type: [link.type, Validators.required],
      url: [link.url, [Validators.required, Validators.maxLength(255), httpsUrl]],
    });
  }
}

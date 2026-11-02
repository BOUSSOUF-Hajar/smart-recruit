import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
import { map } from 'rxjs';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { SkillAutocomplete } from '../../../../shared/ui/skill-autocomplete/skill-autocomplete';
import { SkillOption } from '../../../../shared/ui/skill-autocomplete/skill-search';
import { SKILL_LEVEL_LABELS } from '../data-access/profile-labels';
import { PROFILE_LIMITS, ProfileSkill, SkillLevel } from '../data-access/profile.models';
import { ProfileStore } from '../data-access/profile.store';

type SkillRow = FormGroup<{
  skillId: FormControl<string>;
  name: FormControl<string>;
  level: FormControl<SkillLevel>;
  years: FormControl<number | null>;
}>;

/** US-15 : compétences du référentiel, avec niveau et années ; 50 au plus, sans doublon. */
@Component({
  selector: 'app-skills-editor',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    SkillAutocomplete,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 i18n>Compétences</h2>
    @if (count() < limit) {
      <app-skill-autocomplete [excludedIds]="selectedIds()" (selected)="add($event)" />
    } @else {
      <p class="hint" i18n>Vous avez atteint le maximum de {{ limit }} compétences.</p>
    }

    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <ul formArrayName="skills" class="rows">
        @for (row of rows.controls; track row.controls.skillId.value; let i = $index) {
          <li [formGroupName]="i" class="row">
            <span class="name">{{ row.controls.name.value }}</span>
            <mat-form-field class="level">
              <mat-label i18n>Niveau</mat-label>
              <mat-select formControlName="level">
                @for (level of levels; track level.value) {
                  <mat-option [value]="level.value">{{ level.label }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field class="years">
              <mat-label i18n>Années</mat-label>
              <input matInput type="number" formControlName="years" min="0" max="50" />
              <mat-error>{{ error(row.controls.years.errors) }}</mat-error>
            </mat-form-field>
            <button
              mat-icon-button
              type="button"
              (click)="remove(i)"
              [attr.aria-label]="removeLabel(row.controls.name.value)"
            >
              <mat-icon>close</mat-icon>
            </button>
            @if (row.controls.skillId.errors) {
              <p class="row-error" role="alert">{{ error(row.controls.skillId.errors) }}</p>
            }
          </li>
        } @empty {
          <li class="hint" i18n>Ajoutez au moins trois compétences pour un profil complet.</li>
        }
      </ul>
      <div class="actions">
        <span class="hint">{{ count() }} / {{ limit }}</span>
        <button mat-flat-button type="submit" [disabled]="saving() || form.pristine" i18n>
          Enregistrer les compétences
        </button>
      </div>
    </form>
  `,
  styleUrl: './list-editor.scss',
})
export class SkillsEditor {
  private readonly store = inject(ProfileStore);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly skills = input.required<ProfileSkill[]>();

  protected readonly limit = PROFILE_LIMITS.skills;
  protected readonly levels = Object.entries(SKILL_LEVEL_LABELS).map(([value, label]) => ({
    value: value as SkillLevel,
    label,
  }));
  protected readonly saving = signal(false);

  protected readonly rows = new FormArray<SkillRow>([]);
  protected readonly form = new FormGroup({ skills: this.rows });

  private readonly value = toSignal(
    this.rows.valueChanges.pipe(map(() => this.rows.getRawValue())),
    {
      initialValue: [],
    },
  );
  protected readonly count = computed(() => this.value().length);
  protected readonly selectedIds = computed(() => this.value().map((row) => row.skillId));

  constructor() {
    effect(() => {
      const skills = this.skills();
      if (this.form.pristine) {
        this.rows.clear({ emitEvent: false });
        skills.forEach((skill) => this.rows.push(this.row(skill), { emitEvent: false }));
        this.rows.updateValueAndValidity();
        this.form.markAsPristine();
      }
    });
  }

  protected add(skill: SkillOption): void {
    this.rows.push(
      this.row({ skillId: skill.id, name: skill.name, level: 'INTERMEDIATE', years: null }),
    );
    this.form.markAsDirty();
  }

  protected remove(index: number): void {
    this.rows.removeAt(index);
    this.form.markAsDirty();
  }

  protected removeLabel(name: string): string {
    return $localize`Retirer ${name}:name:`;
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
    const entries = this.rows
      .getRawValue()
      .map(({ skillId, level, years }) => ({ skillId, level, years }));
    this.store.saveSkills(entries).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.markAsPristine();
        this.notifications.success($localize`Compétences enregistrées.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (isApiError(error) && error.code === 'VALIDATION_FAILED') {
          applyServerErrors(this.form, error.errors);
        }
      },
    });
  }

  private row(skill: ProfileSkill): SkillRow {
    return this.fb.group({
      skillId: [skill.skillId],
      name: [skill.name],
      level: [skill.level, Validators.required],
      years: [skill.years, [Validators.min(0), Validators.max(50)]],
    });
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AuthStore } from '../../../../core/auth/auth.store';
import { isApiError } from '../../../../core/errors/api-error';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { httpsUrl } from '../../../../shared/forms/validators';
import {
  INTERVIEW_TYPE_LABELS,
  InterviewType,
  isoToLocalInput,
  localInputToIso,
} from '../../../../shared/models/interview';
import { optionsOf } from '../../../../shared/models/job';
import { InterviewsApi } from '../data-access/interviews.api';
import {
  CompanyRecruiter,
  InterviewRequest,
  RecruiterInterview,
} from '../data-access/interviews.models';

export interface InterviewDialogData {
  applicationId: string;
  /** Présent pour une reprogrammation (US-36), absent pour une planification (US-35). */
  interview?: RecruiterInterview;
}

/** Date-heure locale dans le futur : le serveur le vérifie aussi. */
function inFuture(control: AbstractControl<string>): ValidationErrors | null {
  const iso = localInputToIso(control.value);
  return iso && new Date(iso).getTime() <= Date.now() ? { past: true } : null;
}

const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240];

/**
 * US-35, US-36 : planifier ou reprogrammer. L'heure est saisie dans le fuseau du navigateur et
 * envoyée en UTC (RG-32). Le lien n'est demandé que pour une visioconférence, l'adresse que pour
 * un entretien sur place.
 */
@Component({
  selector: 'app-interview-dialog',
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
    <h2 mat-dialog-title>
      @if (data.interview) {
        <ng-container i18n>Reprogrammer l'entretien</ng-container>
      } @else {
        <ng-container i18n>Planifier un entretien</ng-container>
      }
    </h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="content">
        @if (formError(); as message) {
          <p class="form-error" role="alert">{{ message }}</p>
        }
        <div class="pair">
          <mat-form-field>
            <mat-label i18n>Date et heure</mat-label>
            <input matInput type="datetime-local" formControlName="startsAt" required />
            <mat-hint i18n>Heure de votre fuseau ({{ zone }})</mat-hint>
            <mat-error>{{ error('startsAt') }}</mat-error>
          </mat-form-field>
          <mat-form-field class="short">
            <mat-label i18n>Durée</mat-label>
            <mat-select formControlName="durationMinutes">
              @for (minutes of durations; track minutes) {
                <mat-option [value]="minutes" i18n>{{ minutes }} min</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
        <mat-form-field>
          <mat-label i18n>Type</mat-label>
          <mat-select formControlName="type">
            @for (option of types; track option.value) {
              <mat-option [value]="option.value">{{ option.label }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        @if (type() === 'VIDEO') {
          <mat-form-field>
            <mat-label i18n>Lien de la réunion</mat-label>
            <input
              matInput
              formControlName="meetingUrl"
              type="url"
              placeholder="https://"
              required
            />
            <mat-error>{{ error('meetingUrl') }}</mat-error>
          </mat-form-field>
        }
        @if (type() === 'ON_SITE') {
          <mat-form-field>
            <mat-label i18n>Adresse</mat-label>
            <input matInput formControlName="address" maxlength="255" required />
            <mat-error>{{ error('address') }}</mat-error>
          </mat-form-field>
        }
        <mat-form-field>
          <mat-label i18n>Participants</mat-label>
          <mat-select formControlName="participantIds" multiple required>
            @for (person of recruiters(); track person.id) {
              <mat-option [value]="person.id">{{ person.name }}</mat-option>
            }
          </mat-select>
          <mat-error>{{ error('participantIds') }}</mat-error>
        </mat-form-field>
        <mat-form-field>
          <mat-label i18n>Instructions pour le candidat</mat-label>
          <textarea
            matInput
            formControlName="candidateInstructions"
            rows="3"
            maxlength="1000"
          ></textarea>
          <mat-hint i18n>Visibles par le candidat.</mat-hint>
        </mat-form-field>
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
      min-width: min(480px, 80vw);
    }
    .pair {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
    }
    .pair mat-form-field:first-child {
      flex: 1 1 220px;
    }
    .short {
      width: 120px;
    }
    .form-error {
      color: var(--mat-sys-error);
    }
  `,
})
export class InterviewDialog {
  private readonly api = inject(InterviewsApi);
  private readonly dialogRef =
    inject<MatDialogRef<InterviewDialog, RecruiterInterview>>(MatDialogRef);
  protected readonly data = inject<InterviewDialogData>(MAT_DIALOG_DATA);

  protected readonly types = optionsOf(INTERVIEW_TYPE_LABELS);
  protected readonly durations = DURATIONS;
  protected readonly zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly people = httpResource<CompanyRecruiter[]>(() => '/api/v1/company/recruiters');
  protected readonly recruiters = computed(() =>
    this.people.hasValue() ? this.people.value() : [],
  );

  protected readonly form = this.buildForm();
  protected readonly type = toSignal(this.form.controls.type.valueChanges, {
    initialValue: this.form.controls.type.value,
  });

  constructor() {
    this.form.controls.type.valueChanges.subscribe((type) => this.applyLocationRules(type));
    this.applyLocationRules(this.form.controls.type.value);
  }

  protected error(field: keyof typeof this.form.controls): string | null {
    const errors = this.form.controls[field].errors;
    if (errors?.['past']) {
      return $localize`La date doit être dans le futur.`;
    }
    return fieldErrorMessage(errors);
  }

  protected save(): void {
    this.formError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: InterviewRequest = {
      startsAt: localInputToIso(value.startsAt)!,
      durationMinutes: value.durationMinutes,
      type: value.type,
      meetingUrl: value.type === 'VIDEO' ? value.meetingUrl.trim() || null : null,
      address: value.type === 'ON_SITE' ? value.address.trim() || null : null,
      candidateInstructions: value.candidateInstructions.trim() || null,
      participantIds: value.participantIds,
    };
    const interview = this.data.interview;
    this.saving.set(true);
    const call = interview
      ? this.api.reschedule(interview.id, { ...request, version: interview.version })
      : this.api.schedule(this.data.applicationId, request);
    call.subscribe({
      next: (saved) => this.dialogRef.close(saved),
      error: (error: unknown) => {
        this.saving.set(false);
        if (!isApiError(error)) {
          return;
        }
        if (error.code === 'VALIDATION_FAILED') {
          if (!applyServerErrors(this.form, error.errors)) {
            this.formError.set($localize`Vérifiez les champs du formulaire.`);
          }
        } else if (error.code === 'CONCURRENT_MODIFICATION') {
          this.formError.set(
            $localize`Cet entretien a été modifié entre-temps. Fermez et rouvrez-le.`,
          );
        } else {
          this.formError.set(error.detail ?? error.title);
        }
      },
    });
  }

  private buildForm() {
    const existing = this.data.interview;
    const me = inject(AuthStore).user()?.id;
    return inject(FormBuilder).nonNullable.group({
      startsAt: [
        existing ? isoToLocalInput(existing.startsAt) : '',
        [Validators.required, inFuture],
      ],
      durationMinutes: [existing?.durationMinutes ?? 45],
      type: [(existing?.type ?? 'VIDEO') as InterviewType],
      meetingUrl: [existing?.meetingUrl ?? '', [Validators.maxLength(500), httpsUrl]],
      address: [existing?.address ?? '', Validators.maxLength(255)],
      candidateInstructions: [existing?.candidateInstructions ?? '', Validators.maxLength(1000)],
      participantIds: [
        existing ? existing.participants.map((p) => p.id) : me ? [me] : ([] as string[]),
        Validators.required,
      ],
    });
  }

  /** Lien obligatoire pour VIDEO, adresse pour ON_SITE ; les deux facultatifs sinon. */
  private applyLocationRules(type: InterviewType): void {
    const { meetingUrl, address } = this.form.controls;
    meetingUrl.setValidators(
      type === 'VIDEO'
        ? [Validators.required, Validators.maxLength(500), httpsUrl]
        : [Validators.maxLength(500)],
    );
    address.setValidators(
      type === 'ON_SITE'
        ? [Validators.required, Validators.maxLength(255)]
        : [Validators.maxLength(255)],
    );
    meetingUrl.updateValueAndValidity({ emitEvent: false });
    address.updateValueAndValidity({ emitEvent: false });
  }
}

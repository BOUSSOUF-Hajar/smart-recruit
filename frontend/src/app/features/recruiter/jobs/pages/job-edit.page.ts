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
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Observable, filter, map, startWith, switchMap } from 'rxjs';
import { ApiError, isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { Category } from '../../../../core/referential/referential.api';
import { applyServerErrors } from '../../../../shared/forms/apply-server-errors';
import { fieldErrorMessage } from '../../../../shared/forms/field-error';
import { chronologicalOrder } from '../../../../shared/forms/validators';
import {
  CONTRACT_TYPE_LABELS,
  ContractType,
  EXPERIENCE_LEVEL_LABELS,
  ExperienceLevel,
  SALARY_PERIOD_LABELS,
  SKILL_LEVEL_LABELS,
  SalaryPeriod,
  SkillLevel,
  WORK_MODE_LABELS,
  WorkMode,
  optionsOf,
} from '../../../../shared/models/job';
import { confirm } from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { SkillAutocomplete } from '../../../../shared/ui/skill-autocomplete/skill-autocomplete';
import { SkillOption } from '../../../../shared/ui/skill-autocomplete/skill-search';
import { ExpiryDialog, ExpiryDialogData } from '../components/expiry-dialog';
import { JobStatusChip } from '../components/job-status';
import { RecruiterJobsApi } from '../data-access/recruiter-jobs.api';
import {
  JobRequest,
  LifecycleAction,
  PUBLICATION_REASON_LABELS,
  RecruiterJob,
  availableActions,
  isEditable,
  totalApplications,
} from '../data-access/recruiter-jobs.models';

type RequiredRow = FormGroup<{
  skillId: FormControl<string>;
  name: FormControl<string>;
  minLevel: FormControl<SkillLevel>;
}>;
type PreferredRow = FormGroup<{ skillId: FormControl<string>; name: FormControl<string> }>;

/**
 * US-21 à US-23 : création, modification et cycle de vie d'une offre. Le formulaire passe en lecture
 * seule pour une offre clôturée ou archivée (RG-17).
 */
@Component({
  selector: 'app-job-edit-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    JobStatusChip,
    SkillAutocomplete,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './job-edit.page.html',
  styleUrl: './job-edit.page.scss',
})
export class JobEditPage {
  private readonly api = inject(RecruiterJobsApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(FormBuilder).nonNullable;

  /** Paramètre de route `:id` ; absent pour une nouvelle offre. */
  readonly id = input<string>();

  protected readonly categories = httpResource<Category[]>(() => '/api/v1/categories');
  protected readonly job = signal<RecruiterJob | null>(null);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly conflict = signal(false);
  protected readonly publicationReasons = signal<string[]>([]);

  protected readonly workModes = optionsOf(WORK_MODE_LABELS);
  protected readonly contractTypes = optionsOf(CONTRACT_TYPE_LABELS);
  protected readonly levels = optionsOf(EXPERIENCE_LEVEL_LABELS);
  protected readonly skillLevels = optionsOf(SKILL_LEVEL_LABELS);
  protected readonly periods = optionsOf(SALARY_PERIOD_LABELS);

  protected readonly requiredSkills = new FormArray<RequiredRow>([]);
  protected readonly preferredSkills = new FormArray<PreferredRow>([]);
  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.required, Validators.minLength(50), Validators.maxLength(10000)]],
    categoryId: ['', Validators.required],
    location: this.fb.group({
      city: ['', [Validators.required, Validators.maxLength(80)]],
      countryCode: ['MA', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    }),
    workMode: ['HYBRID' as WorkMode, Validators.required],
    contractType: ['CDI' as ContractType, Validators.required],
    experienceLevel: ['MID' as ExperienceLevel, Validators.required],
    minYearsExperience: [0, [Validators.min(0), Validators.max(30)]],
    salary: this.fb.group(
      {
        min: [null as number | null, Validators.min(0)],
        max: [null as number | null, Validators.min(0)],
        currency: ['MAD', Validators.pattern(/^[A-Za-z]{3}$/)],
        period: ['MONTHLY' as SalaryPeriod],
        visible: [false],
      },
      { validators: chronologicalOrder('min', 'max') },
    ),
    requiredSkills: this.requiredSkills,
    preferredSkills: this.preferredSkills,
  });

  /** Identifiants des compétences déjà choisies dans l'une ou l'autre liste (une seule fois par offre). */
  protected readonly selectedSkillIds = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() =>
        [...this.requiredSkills.getRawValue(), ...this.preferredSkills.getRawValue()].map(
          (s) => s.skillId,
        ),
      ),
    ),
    { initialValue: [] as string[] },
  );

  protected readonly editable = computed(() => {
    const job = this.job();
    return job === null || isEditable(job.status);
  });
  protected readonly actions = computed(() => {
    const job = this.job();
    return job ? availableActions(job.status) : [];
  });
  protected readonly applications = computed(() =>
    totalApplications(this.job()?.applicationCounts ?? {}),
  );

  constructor() {
    effect(() => {
      const id = this.id();
      if (id) {
        this.load(id);
      }
    });
  }

  protected error(path: string): string | null {
    const control = this.form.get(path);
    return control ? fieldErrorMessage(control.errors) : null;
  }

  protected rowError(row: FormGroup): string | null {
    return fieldErrorMessage(row.get('skillId')?.errors ?? null);
  }

  protected addRequired(skill: SkillOption): void {
    this.requiredSkills.push(this.requiredRow(skill.id, skill.name, 'INTERMEDIATE'));
    this.form.markAsDirty();
  }

  protected addPreferred(skill: SkillOption): void {
    this.preferredSkills.push(this.preferredRow(skill.id, skill.name));
    this.form.markAsDirty();
  }

  protected removeRequired(index: number): void {
    this.requiredSkills.removeAt(index);
    this.form.markAsDirty();
  }

  protected removePreferred(index: number): void {
    this.preferredSkills.removeAt(index);
    this.form.markAsDirty();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const job = this.job();
    const request = this.request(job?.version);
    this.saving.set(true);
    this.conflict.set(false);
    const call = job ? this.api.update(job.id, request) : this.api.create(request);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.notifications.success($localize`Offre enregistrée.`);
        if (!job) {
          void this.router.navigate(['/recruiter/jobs', saved.id]);
        }
        this.show(saved);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.handleError(error);
      },
    });
  }

  protected run(action: LifecycleAction): void {
    const job = this.job();
    if (!job) {
      return;
    }
    this.publicationReasons.set([]);
    this.prepare(action, job).subscribe({
      next: (result) => {
        if (result === 'deleted') {
          this.notifications.success($localize`Brouillon supprimé.`);
          void this.router.navigate(['/recruiter/jobs']);
        } else {
          this.notifications.success($localize`Offre mise à jour.`);
          this.show(result);
        }
      },
      error: (error: unknown) => this.handleError(error),
    });
  }

  protected actionLabel(action: LifecycleAction): string {
    return ACTION_LABELS[action];
  }

  protected reload(): void {
    const job = this.job();
    if (job) {
      this.conflict.set(false);
      this.load(job.id);
    }
  }

  /** Confirmation ou date d'expiration demandée avant l'appel, selon l'action. */
  private prepare(
    action: LifecycleAction,
    job: RecruiterJob,
  ): Observable<RecruiterJob | 'deleted'> {
    switch (action) {
      case 'publish':
      case 'reopen':
        return this.askExpiry(action).pipe(
          switchMap((date) =>
            action === 'publish' ? this.api.publish(job.id, date) : this.api.reopen(job.id, date),
          ),
        );
      case 'unpublish':
        return this.api.unpublish(job.id);
      case 'close':
        return this.confirmed(
          $localize`Clôturer cette offre ?`,
          $localize`Elle ne recevra plus de candidatures ; celles en cours continuent.`,
          $localize`Clôturer`,
        ).pipe(switchMap(() => this.api.close(job.id)));
      case 'archive':
        return this.confirmed(
          $localize`Archiver cette offre ?`,
          $localize`Elle disparaîtra de la liste par défaut. L'archivage est définitif.`,
          $localize`Archiver`,
        ).pipe(switchMap(() => this.api.archive(job.id)));
      case 'delete':
        return this.confirmed(
          $localize`Supprimer ce brouillon ?`,
          job.title,
          $localize`Supprimer`,
        ).pipe(
          switchMap(() => this.api.delete(job.id)),
          map(() => 'deleted' as const),
        );
    }
  }

  private askExpiry(action: 'publish' | 'reopen'): Observable<string> {
    const data: ExpiryDialogData =
      action === 'publish'
        ? { title: $localize`Publier l'offre`, confirmLabel: $localize`Publier` }
        : { title: $localize`Rouvrir l'offre`, confirmLabel: $localize`Rouvrir` };
    return this.dialog
      .open<ExpiryDialog, ExpiryDialogData, string>(ExpiryDialog, { data })
      .afterClosed()
      .pipe(filter((date): date is string => typeof date === 'string'));
  }

  private confirmed(title: string, message: string, confirmLabel: string): Observable<true> {
    return confirm(this.dialog, { title, message, confirmLabel }).pipe(
      filter((ok): ok is true => ok),
    );
  }

  private handleError(error: unknown): void {
    if (!isApiError(error)) {
      return;
    }
    switch (error.code) {
      case 'CONCURRENT_MODIFICATION':
        this.conflict.set(true);
        break;
      case 'JOB_NOT_PUBLISHABLE':
        this.publicationReasons.set(reasonsOf(error));
        break;
      case 'VALIDATION_FAILED':
        applyServerErrors(this.form, error.errors);
        break;
      case 'INVALID_STATE':
      case 'INVALID_STATUS_TRANSITION':
      case 'RESOURCE_IN_USE':
        this.notifications.error(error.detail ?? error.title);
        break;
    }
  }

  private load(id: string): void {
    this.api.get(id).subscribe({
      next: (job) => this.show(job),
      error: () => this.loadError.set(true),
    });
  }

  /** Affiche l'offre reçue du serveur, qui fait foi ; lecture seule si elle n'est plus modifiable. */
  private show(job: RecruiterJob): void {
    this.job.set(job);
    this.requiredSkills.clear({ emitEvent: false });
    job.requiredSkills.forEach((skill) =>
      this.requiredSkills.push(
        this.requiredRow(skill.skillId, skill.name, skill.minLevel ?? 'INTERMEDIATE'),
        {
          emitEvent: false,
        },
      ),
    );
    this.preferredSkills.clear({ emitEvent: false });
    job.preferredSkills.forEach((skill) =>
      this.preferredSkills.push(this.preferredRow(skill.skillId, skill.name), { emitEvent: false }),
    );
    this.form.reset({
      title: job.title,
      description: job.description,
      categoryId: job.categoryId,
      location: { ...job.location },
      workMode: job.workMode,
      contractType: job.contractType,
      experienceLevel: job.experienceLevel,
      minYearsExperience: job.minYearsExperience,
      salary: job.salary
        ? { ...job.salary, period: job.salary.period ?? 'MONTHLY' }
        : { min: null, max: null, currency: 'MAD', period: 'MONTHLY', visible: false },
    });
    if (isEditable(job.status)) {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
  }

  private request(version: number | undefined): JobRequest {
    const value = this.form.getRawValue();
    const salary = value.salary;
    const hasSalary = salary.min !== null || salary.max !== null;
    return {
      title: value.title.trim(),
      description: value.description.trim(),
      categoryId: value.categoryId,
      location: {
        city: value.location.city.trim(),
        countryCode: value.location.countryCode.toUpperCase(),
      },
      workMode: value.workMode,
      contractType: value.contractType,
      experienceLevel: value.experienceLevel,
      minYearsExperience: value.minYearsExperience ?? 0,
      salary: hasSalary ? { ...salary, currency: salary.currency.toUpperCase() } : null,
      requiredSkills: this.requiredSkills
        .getRawValue()
        .map(({ skillId, minLevel }) => ({ skillId, minLevel })),
      preferredSkills: this.preferredSkills.getRawValue().map(({ skillId }) => ({ skillId })),
      version,
    };
  }

  private requiredRow(skillId: string, name: string, minLevel: SkillLevel): RequiredRow {
    return this.fb.group({
      skillId: [skillId],
      name: [name],
      minLevel: [minLevel, Validators.required],
    });
  }

  private preferredRow(skillId: string, name: string): PreferredRow {
    return this.fb.group({ skillId: [skillId], name: [name] });
  }
}

const ACTION_LABELS: Record<LifecycleAction, string> = {
  publish: $localize`Publier`,
  unpublish: $localize`Repasser en brouillon`,
  close: $localize`Clôturer`,
  reopen: $localize`Rouvrir`,
  archive: $localize`Archiver`,
  delete: $localize`Supprimer`,
};

function reasonsOf(error: ApiError): string[] {
  const reasons = error.extensions['reasons'];
  return Array.isArray(reasons)
    ? reasons.map((reason) => PUBLICATION_REASON_LABELS[String(reason)] ?? String(reason))
    : [error.detail ?? error.title];
}

import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocompleteModule,
  MatAutocompleteSelectedEvent,
} from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { SKILL_SEARCH, SkillOption, normalizeSkillName } from './skill-search';

/** Valeur spéciale de l'option « Ajouter « … » au référentiel ». */
const CREATE_OPTION = '__create__';

/** Saisie minimale avant de chercher, et délai après la dernière frappe (architecture frontend §5). */
export const MIN_QUERY_LENGTH = 2;
export const SEARCH_DEBOUNCE_MS = 300;

/**
 * Choix d'une compétence dans le référentiel, avec création si elle est absente (US-15).
 * Émet la compétence choisie puis vide le champ, pour en ajouter une autre aussitôt.
 */
@Component({
  selector: 'app-skill-autocomplete',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-form-field class="field">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [formControl]="query"
        [matAutocomplete]="auto"
        autocomplete="off"
        maxlength="80"
      />
      <mat-hint i18n>Tapez au moins 2 lettres, par exemple « spr ».</mat-hint>
      <mat-autocomplete
        #auto="matAutocomplete"
        [displayWith]="displayNothing"
        (optionSelected)="choose($event)"
      >
        @for (option of options(); track option.id) {
          <mat-option [value]="option.id" [disabled]="excluded().has(option.id)">
            {{ option.name }}
            @if (excluded().has(option.id)) {
              <small i18n>(déjà ajoutée)</small>
            }
          </mat-option>
        }
        @if (canCreate()) {
          <mat-option [value]="createValue" i18n
            >Ajouter « {{ trimmedQuery() }} » au référentiel</mat-option
          >
        }
      </mat-autocomplete>
    </mat-form-field>
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
  `,
  styles: `
    .field {
      width: 100%;
    }
    .error {
      margin: 0;
      color: var(--mat-sys-error);
      font: var(--mat-sys-body-small);
    }
  `,
})
export class SkillAutocomplete {
  private readonly skills = inject(SKILL_SEARCH);

  readonly label = input($localize`Ajouter une compétence`);
  /** Compétences déjà présentes dans la liste du formulaire : proposées mais non sélectionnables. */
  readonly excludedIds = input<readonly string[]>([]);
  /** Faux pour un filtre : on cherche dans le référentiel sans pouvoir l'enrichir. */
  readonly allowCreate = input(true);
  readonly selected = output<SkillOption>();

  protected readonly createValue = CREATE_OPTION;
  /** Le champ sert à chercher : après un choix, il se vide au lieu d'afficher l'identifiant. */
  protected readonly displayNothing = (): string => '';
  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly error = signal<string | null>(null);
  protected readonly excluded = computed(() => new Set(this.excludedIds()));

  protected readonly trimmedQuery = toSignal(
    this.query.valueChanges.pipe(map((value) => value.trim().replace(/\s+/g, ' '))),
    { initialValue: '' },
  );

  protected readonly options = toSignal(
    this.query.valueChanges.pipe(
      startWith(''),
      map((value) => value.trim()),
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      // switchMap annule la recherche précédente : une réponse lente n'écrase jamais une plus récente
      switchMap((value) =>
        value.length < MIN_QUERY_LENGTH
          ? of([] as SkillOption[])
          : this.skills.search(value).pipe(catchError(() => of([] as SkillOption[]))),
      ),
    ),
    { initialValue: [] as SkillOption[] },
  );

  /** Pas d'option de création si une proposition porte déjà exactement ce nom. */
  protected readonly canCreate = computed(() => {
    const typed = this.trimmedQuery();
    if (!this.allowCreate() || typed.length < MIN_QUERY_LENGTH) {
      return false;
    }
    const normalized = normalizeSkillName(typed);
    return !this.options().some((option) => normalizeSkillName(option.name) === normalized);
  });

  protected choose(event: MatAutocompleteSelectedEvent): void {
    const value = event.option.value as string;
    this.error.set(null);
    if (value === CREATE_OPTION) {
      this.skills.create(this.trimmedQuery()).subscribe({
        next: (skill) => this.emit(skill),
        error: () => this.error.set($localize`Cette compétence n'a pas pu être ajoutée.`),
      });
      return;
    }
    const option = this.options().find((candidate) => candidate.id === value);
    if (option) {
      this.emit(option);
    }
  }

  private emit(skill: SkillOption): void {
    if (this.excluded().has(skill.id)) {
      this.error.set($localize`Cette compétence est déjà dans votre liste.`);
    } else {
      this.selected.emit(skill);
    }
    this.query.setValue('');
  }
}

import { AbstractControl, FormGroup } from '@angular/forms';

/** Erreur de champ renvoyée par l'API avec VALIDATION_FAILED. */
export interface ServerFieldError {
  field: string;
  message: string;
}

/**
 * Place chaque erreur de validation du serveur sous le champ correspondant, avec la clé `server`.
 * Les chemins de liste du serveur (`skills[2].skillId`) sont traduits en chemins Angular
 * (`skills.2.skillId`). Les champs inconnus du formulaire sont ignorés.
 *
 * @returns `true` si au moins une erreur a été placée ; sinon l'écran affiche un message général
 */
export function applyServerErrors(form: FormGroup, errors: readonly ServerFieldError[]): boolean {
  let applied = false;
  for (const error of errors) {
    const control: AbstractControl | null = form.get(toFormPath(error.field));
    if (control) {
      control.setErrors({ ...control.errors, server: error.message });
      control.markAsTouched();
      applied = true;
    }
  }
  return applied;
}

/** `skills[2].skillId` → `skills.2.skillId` */
export function toFormPath(field: string): string {
  return field.replace(/\[(\d+)\]/g, '.$1');
}

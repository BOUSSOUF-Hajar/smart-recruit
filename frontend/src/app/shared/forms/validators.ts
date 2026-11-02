import { AbstractControl, FormArray, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Validateur de groupe : `confirmation` doit être égal à `source`. L'erreur `mismatch` est posée
 * sur le champ de confirmation, pour s'afficher sous lui ; fonction pure, testable sans composant.
 */
export function matchingFields(source: string, confirmation: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const confirmControl = group.get(confirmation);
    const matches = group.get(source)?.value === confirmControl?.value;
    if (!confirmControl) {
      return null;
    }
    const others: ValidationErrors = { ...confirmControl.errors };
    delete others['mismatch'];
    if (matches) {
      confirmControl.setErrors(Object.keys(others).length ? others : null);
      return null;
    }
    confirmControl.setErrors({ ...others, mismatch: true });
    return { mismatch: true };
  };
}

/** Format international E.164, comme le backend : « + », indicatif, 7 à 15 chiffres. */
export const E164_PHONE = /^\+[1-9]\d{6,14}$/;

/** Téléphone au format E.164 ; vide accepté (champ facultatif). */
export function e164Phone(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value !== 'string' || value === '' || E164_PHONE.test(value)
    ? null
    : { phone: true };
}

/**
 * Validateur de groupe : `end` ne précède pas `start` (dates ISO `YYYY-MM-DD` ou années).
 * L'erreur `chronology` est posée sur le champ de fin, comme `@ChronologicalOrder` côté serveur.
 */
export function chronologicalOrder(start: string, end: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const endControl = group.get(end);
    const startValue: unknown = group.get(start)?.value;
    const endValue: unknown = endControl?.value;
    if (!endControl) {
      return null;
    }
    const others: ValidationErrors = { ...endControl.errors };
    delete others['chronology'];
    const comparable = (value: unknown) => value !== null && value !== undefined && value !== '';
    const inOrder =
      !comparable(startValue) ||
      !comparable(endValue) ||
      String(endValue).localeCompare(String(startValue), undefined, { numeric: true }) >= 0;
    if (inOrder) {
      endControl.setErrors(Object.keys(others).length ? others : null);
      return null;
    }
    endControl.setErrors({ ...others, chronology: true });
    return { chronology: true };
  };
}

/** Date ISO `YYYY-MM-DD` qui n'est pas dans le futur (date de début d'un poste). */
export function notInFuture(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value === '') {
    return null;
  }
  const today = new Date().toISOString().slice(0, 10);
  return value <= today ? null : { future: true };
}

/**
 * Validateur de `FormArray` : chaque valeur du champ `key` n'apparaît qu'une fois. L'erreur
 * `duplicate` est posée sur le champ des lignes en double (à partir de la deuxième occurrence).
 */
export function uniqueBy(key: string): ValidatorFn {
  return (array: AbstractControl): ValidationErrors | null => {
    if (!(array instanceof FormArray)) {
      return null;
    }
    const seen = new Set<unknown>();
    let duplicated = false;
    for (const row of array.controls) {
      const control = row.get(key);
      if (!control) {
        continue;
      }
      const others: ValidationErrors = { ...control.errors };
      delete others['duplicate'];
      const value: unknown = control.value;
      if (value !== '' && value !== null && seen.has(value)) {
        control.setErrors({ ...others, duplicate: true });
        duplicated = true;
      } else {
        control.setErrors(Object.keys(others).length ? others : null);
        seen.add(value);
      }
    }
    return duplicated ? { duplicate: true } : null;
  };
}

/**
 * Adresse https absolue avec un nom d'hôte, comme `@HttpsUrl` côté serveur :
 * refuse `http:`, `javascript:` et les adresses relatives.
 */
export function httpsUrl(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  if (typeof value !== 'string' || value === '') {
    return null;
  }
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname.includes('.') && value.startsWith('https://')
      ? null
      : { httpsUrl: true };
  } catch {
    return { httpsUrl: true };
  }
}

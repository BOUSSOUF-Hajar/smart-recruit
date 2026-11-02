import { ValidationErrors } from '@angular/forms';

/**
 * Un seul message par champ, dans un ordre de priorité fixe : l'utilisateur corrige
 * une chose à la fois. Les erreurs du serveur passent en premier, elles sont les plus précises.
 */
export function fieldErrorMessage(errors: ValidationErrors | null): string | null {
  if (!errors) {
    return null;
  }
  if (typeof errors['server'] === 'string') {
    return errors['server'];
  }
  if (errors['required']) {
    return $localize`Ce champ est obligatoire.`;
  }
  if (errors['email']) {
    return $localize`Saisissez une adresse email valide.`;
  }
  if (errors['minlength']) {
    const { requiredLength } = errors['minlength'] as { requiredLength: number };
    return $localize`Au moins ${requiredLength}:min: caractères.`;
  }
  if (errors['maxlength']) {
    const { requiredLength } = errors['maxlength'] as { requiredLength: number };
    return $localize`Au plus ${requiredLength}:max: caractères.`;
  }
  if (errors['min']) {
    const { min } = errors['min'] as { min: number };
    return $localize`Au moins ${min}:min:.`;
  }
  if (errors['max']) {
    const { max } = errors['max'] as { max: number };
    return $localize`Au plus ${max}:max:.`;
  }
  if (errors['httpsUrl']) {
    return $localize`Saisissez une adresse commençant par https://.`;
  }
  if (errors['phone']) {
    return $localize`Format international attendu, par exemple +212600000000.`;
  }
  if (errors['chronology']) {
    return $localize`La fin ne peut pas précéder le début.`;
  }
  if (errors['future']) {
    return $localize`La date ne peut pas être dans le futur.`;
  }
  if (errors['duplicate']) {
    return $localize`Déjà dans la liste.`;
  }
  if (errors['mismatch']) {
    return $localize`Les deux saisies ne correspondent pas.`;
  }
  return $localize`Valeur invalide.`;
}

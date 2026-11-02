import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

/** Compétence du référentiel telle que la proposent les listes de choix. */
export interface SkillOption {
  id: string;
  name: string;
}

/**
 * Accès au référentiel de compétences, dont le composant partagé a besoin sans pouvoir faire
 * d'appel HTTP lui-même (règle de `shared`). `core` fournit l'implémentation dans `app.config.ts`.
 */
export interface SkillSearch {
  search(query: string): Observable<SkillOption[]>;
  /** « Obtenir ou créer » : renvoie la compétence existante si le nom (ou un alias) est déjà connu. */
  create(name: string): Observable<SkillOption>;
}

export const SKILL_SEARCH = new InjectionToken<SkillSearch>('SKILL_SEARCH');

/** Même règle que le backend (`SkillNames.normalize`) : minuscules, espaces réduits. */
export function normalizeSkillName(value: string): string {
  return value.normalize('NFKC').trim().replace(/\s+/g, ' ').toLowerCase();
}

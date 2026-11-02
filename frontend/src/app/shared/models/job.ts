/**
 * Énumérations des offres et leurs libellés, partagés par la recherche publique et l'espace
 * recruteur. L'API envoie des codes ; l'interface choisit les mots.
 */
export type WorkMode = 'ON_SITE' | 'HYBRID' | 'REMOTE';
export type ContractType = 'CDI' | 'CDD' | 'INTERNSHIP' | 'FREELANCE' | 'APPRENTICESHIP';
export type ExperienceLevel = 'JUNIOR' | 'MID' | 'SENIOR' | 'LEAD';
export type SalaryPeriod = 'MONTHLY' | 'YEARLY';
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED';
export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

export const WORK_MODE_LABELS: Record<WorkMode, string> = {
  ON_SITE: $localize`Sur site`,
  HYBRID: $localize`Hybride`,
  REMOTE: $localize`Télétravail`,
};

export const CONTRACT_TYPE_LABELS: Record<ContractType, string> = {
  CDI: 'CDI',
  CDD: 'CDD',
  INTERNSHIP: $localize`Stage`,
  FREELANCE: $localize`Freelance`,
  APPRENTICESHIP: $localize`Alternance`,
};

export const EXPERIENCE_LEVEL_LABELS: Record<ExperienceLevel, string> = {
  JUNIOR: $localize`Junior`,
  MID: $localize`Confirmé`,
  SENIOR: $localize`Senior`,
  LEAD: $localize`Lead`,
};

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  DRAFT: $localize`Brouillon`,
  PUBLISHED: $localize`Publiée`,
  CLOSED: $localize`Clôturée`,
  ARCHIVED: $localize`Archivée`,
};

export const SKILL_LEVEL_LABELS: Record<SkillLevel, string> = {
  BEGINNER: $localize`Débutant`,
  INTERMEDIATE: $localize`Intermédiaire`,
  ADVANCED: $localize`Avancé`,
  EXPERT: $localize`Expert`,
};

export const SALARY_PERIOD_LABELS: Record<SalaryPeriod, string> = {
  MONTHLY: $localize`par mois`,
  YEARLY: $localize`par an`,
};

/** Options d'un `mat-select` à partir d'une table de libellés. */
export function optionsOf<T extends string>(
  labels: Record<T, string>,
): { value: T; label: string }[] {
  return (Object.entries(labels) as [T, string][]).map(([value, label]) => ({ value, label }));
}

export interface Salary {
  min: number | null;
  max: number | null;
  currency: string;
  period: SalaryPeriod | null;
}

/** « 15 000 – 22 000 MAD par mois » ; une seule borne si l'autre manque. */
export function formatSalary(salary: Salary | null): string | null {
  if (!salary || (salary.min === null && salary.max === null)) {
    return null;
  }
  const amount = (value: number) => value.toLocaleString('fr-FR', { maximumFractionDigits: 0 });
  const range =
    salary.min !== null && salary.max !== null
      ? `${amount(salary.min)} – ${amount(salary.max)}`
      : salary.min !== null
        ? $localize`à partir de ${amount(salary.min)}:min:`
        : $localize`jusqu'à ${amount(salary.max as number)}:max:`;
  const period = salary.period ? ` ${SALARY_PERIOD_LABELS[salary.period]}` : '';
  return `${range} ${salary.currency}${period}`;
}

/** Réponse paginée de l'API (docs/api.md §0). */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

import {
  ContractType,
  ExperienceLevel,
  JobStatus,
  SalaryPeriod,
  SkillLevel,
  WorkMode,
} from '../../../../shared/models/job';

export interface RecruiterSalary {
  min: number | null;
  max: number | null;
  currency: string;
  period: SalaryPeriod | null;
  visible: boolean;
}

export interface RecruiterSkill {
  skillId: string;
  name: string;
  minLevel: SkillLevel | null;
}

/** Vue recruteur d'une offre (docs/api.md §6). */
export interface RecruiterJob {
  id: string;
  title: string;
  description: string;
  categoryId: string;
  location: { city: string; countryCode: string };
  workMode: WorkMode;
  contractType: ContractType;
  experienceLevel: ExperienceLevel;
  minYearsExperience: number;
  salary: RecruiterSalary | null;
  requiredSkills: RecruiterSkill[];
  preferredSkills: RecruiterSkill[];
  status: JobStatus;
  publishedAt: string | null;
  expiresAt: string | null;
  /** Publiée mais date d'expiration dépassée : invisible du public. */
  expired: boolean;
  createdBy: { id: string; name: string };
  applicationCounts: Record<string, number>;
  version: number;
}

export interface RecruiterJobSummary {
  id: string;
  title: string;
  status: JobStatus;
  city: string;
  contractType: ContractType;
  publishedAt: string | null;
  expiresAt: string | null;
  expired: boolean;
  applicationCounts: Record<string, number>;
  updatedAt: string;
}

export interface JobRequest {
  title: string;
  description: string;
  categoryId: string;
  location: { city: string; countryCode: string };
  workMode: WorkMode;
  contractType: ContractType;
  experienceLevel: ExperienceLevel;
  minYearsExperience: number;
  salary: RecruiterSalary | null;
  requiredSkills: { skillId: string; minLevel: SkillLevel }[];
  preferredSkills: { skillId: string }[];
  version?: number;
}

export type LifecycleAction = 'publish' | 'unpublish' | 'close' | 'reopen' | 'archive' | 'delete';

/**
 * Actions possibles selon le statut (diagramme d'états, analyse fonctionnelle §4.2). Seulement
 * pour l'affichage : le serveur refuse de toute façon une transition interdite.
 */
export function availableActions(status: JobStatus): LifecycleAction[] {
  switch (status) {
    case 'DRAFT':
      return ['publish', 'delete'];
    case 'PUBLISHED':
      return ['close', 'unpublish'];
    case 'CLOSED':
      return ['reopen', 'archive'];
    case 'ARCHIVED':
      return [];
  }
}

/** RG-17 : modifiable en brouillon et publiée, lecture seule ensuite. */
export function isEditable(status: JobStatus): boolean {
  return status === 'DRAFT' || status === 'PUBLISHED';
}

/** Nombre total de candidatures, tous statuts confondus. */
export function totalApplications(counts: Record<string, number>): number {
  return Object.values(counts).reduce((sum, count) => sum + count, 0);
}

/** Raisons de refus de publication (`JOB_NOT_PUBLISHABLE`) en clair. */
export const PUBLICATION_REASON_LABELS: Record<string, string> = {
  EXPIRY_IN_PAST: $localize`La date d'expiration doit être dans le futur.`,
  EXPIRY_TOO_FAR: $localize`La date d'expiration doit être à 180 jours au plus.`,
  NO_REQUIRED_SKILL: $localize`Ajoutez au moins une compétence requise.`,
};

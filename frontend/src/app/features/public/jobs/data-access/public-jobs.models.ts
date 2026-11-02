import {
  ContractType,
  ExperienceLevel,
  Salary,
  SkillLevel,
  WorkMode,
} from '../../../../shared/models/job';

export interface Ref {
  id: string;
  name: string;
}

/** Élément de la liste de recherche (docs/api.md §3). */
export interface JobCard {
  id: string;
  title: string;
  company: Ref;
  location: { city: string; countryCode: string };
  workMode: WorkMode;
  contractType: ContractType;
  experienceLevel: ExperienceLevel;
  /** `null` si le recruteur n'a pas rendu le salaire public. */
  salary: Salary | null;
  topSkills: string[];
  publishedAt: string;
  expiresAt: string;
}

export interface JobDetail extends Omit<JobCard, 'topSkills'> {
  description: string;
  category: Ref;
  minYearsExperience: number;
  requiredSkills: (Ref & { minLevel: SkillLevel })[];
  preferredSkills: Ref[];
  /** Pour un candidat connecté ; absent pour les autres visiteurs. */
  alreadyApplied?: boolean | null;
  /** Candidature du candidat connecté à cette offre, s'il a postulé (US-26). */
  myApplicationId?: string | null;
}

/** Filtres de la recherche, tels qu'ils vivent dans l'URL (architecture frontend §8). */
export interface JobFilters {
  q?: string;
  city?: string;
  workMode?: string;
  contractType?: string;
  experienceLevel?: string;
  categoryId?: string;
  salaryMin?: string;
  sort?: string;
  page?: string;
}

/** Paramètres HTTP à partir des filtres de l'URL ; les valeurs vides sont omises. */
export function searchParams(filters: JobFilters): Record<string, string> {
  const params: Record<string, string> = {};
  for (const [key, value] of Object.entries(filters)) {
    if (typeof value === 'string' && value.trim() !== '') {
      params[key] = value.trim();
    }
  }
  return params;
}

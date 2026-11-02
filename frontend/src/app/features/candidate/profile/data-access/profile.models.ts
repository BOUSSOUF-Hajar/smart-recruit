/** Miroir des DTO de `docs/api.md` §4. */

export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';
export type LanguageLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2' | 'NATIVE';
export type LinkType = 'LINKEDIN' | 'GITHUB' | 'PORTFOLIO' | 'OTHER';

export interface Location {
  city: string | null;
  countryCode: string | null;
}

export interface Experience {
  id: string;
  jobTitle: string;
  companyName: string;
  city: string | null;
  /** `YYYY-MM-DD` */
  startDate: string;
  endDate: string | null;
  current: boolean;
  description: string | null;
}

export interface Education {
  id: string;
  degree: string;
  school: string;
  fieldOfStudy: string | null;
  startYear: number | null;
  endYear: number | null;
}

export interface ProfileSkill {
  skillId: string;
  name: string;
  level: SkillLevel;
  years: number | null;
}

export interface ProfileLanguage {
  languageCode: string;
  level: LanguageLevel;
}

export interface ProfileLink {
  type: LinkType;
  url: string;
}

export interface ResumeInfo {
  id: string;
  originalFilename: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface Completeness {
  percent: number;
  /** Critères manquants, dans l'ordre de la grille (analyse fonctionnelle §6.1). */
  missing: CompletenessCriterion[];
}

export type CompletenessCriterion =
  'name' | 'headline' | 'summary' | 'experience' | 'education' | 'skills' | 'language' | 'resume';

export interface CandidateProfile {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  location: Location | null;
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  experiences: Experience[];
  educations: Education[];
  skills: ProfileSkill[];
  languages: ProfileLanguage[];
  links: ProfileLink[];
  resume: ResumeInfo | null;
  completeness: Completeness;
  version: number;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  phone: string | null;
  location: Location | null;
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  version: number;
}

export type ExperienceRequest = Omit<Experience, 'id' | 'current'>;
export type EducationRequest = Omit<Education, 'id'>;

export interface SkillEntryRequest {
  skillId: string;
  level: SkillLevel;
  years: number | null;
}

/** Limites du serveur (`ProfileRules`), reprises pour un retour immédiat. */
export const PROFILE_LIMITS = { skills: 50, languages: 20, links: 5 } as const;

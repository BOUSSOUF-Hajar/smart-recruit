import { CandidateProfile } from './profile.models';

/** Profil de test, à compléter selon le cas. */
export function aProfile(overrides: Partial<CandidateProfile> = {}): CandidateProfile {
  return {
    firstName: 'Salma',
    lastName: 'Idrissi',
    email: 'salma@example.com',
    phone: null,
    location: null,
    headline: null,
    summary: null,
    yearsOfExperience: null,
    experiences: [],
    educations: [],
    skills: [],
    languages: [],
    links: [],
    resume: null,
    completeness: {
      percent: 10,
      missing: ['headline', 'summary', 'experience', 'education', 'skills', 'language', 'resume'],
    },
    version: 3,
    ...overrides,
  };
}

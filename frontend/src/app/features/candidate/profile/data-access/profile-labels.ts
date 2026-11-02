import { CompletenessCriterion, LanguageLevel, LinkType, SkillLevel } from './profile.models';

/** Libellés des énumérations de l'API : le serveur envoie des codes, l'interface choisit les mots. */
export const SKILL_LEVEL_LABELS: Record<SkillLevel, string> = {
  BEGINNER: $localize`Débutant`,
  INTERMEDIATE: $localize`Intermédiaire`,
  ADVANCED: $localize`Avancé`,
  EXPERT: $localize`Expert`,
};

export const LANGUAGE_LEVEL_LABELS: Record<LanguageLevel, string> = {
  A1: 'A1',
  A2: 'A2',
  B1: 'B1',
  B2: 'B2',
  C1: 'C1',
  C2: 'C2',
  NATIVE: $localize`Langue maternelle`,
};

export const LINK_TYPE_LABELS: Record<LinkType, string> = {
  LINKEDIN: 'LinkedIn',
  GITHUB: 'GitHub',
  PORTFOLIO: $localize`Portfolio`,
  OTHER: $localize`Autre`,
};

/** Langues proposées (ISO 639) ; la liste reste courte pour un choix rapide. */
export const LANGUAGES: readonly { code: string; label: string }[] = [
  { code: 'ar', label: $localize`Arabe` },
  { code: 'zgh', label: $localize`Amazighe` },
  { code: 'fr', label: $localize`Français` },
  { code: 'en', label: $localize`Anglais` },
  { code: 'es', label: $localize`Espagnol` },
  { code: 'de', label: $localize`Allemand` },
  { code: 'it', label: $localize`Italien` },
  { code: 'pt', label: $localize`Portugais` },
  { code: 'nl', label: $localize`Néerlandais` },
  { code: 'tr', label: $localize`Turc` },
  { code: 'zh', label: $localize`Chinois` },
  { code: 'ja', label: $localize`Japonais` },
  { code: 'ru', label: $localize`Russe` },
];

export function languageLabel(code: string): string {
  return LANGUAGES.find((language) => language.code === code)?.label ?? code;
}

/** Critère manquant → libellé et onglet où le compléter. */
export const CRITERION_LABELS: Record<CompletenessCriterion, { label: string; tab: number }> = {
  name: { label: $localize`Prénom et nom`, tab: 0 },
  headline: { label: $localize`Titre professionnel`, tab: 0 },
  summary: { label: $localize`Résumé`, tab: 0 },
  experience: { label: $localize`Une expérience`, tab: 1 },
  education: { label: $localize`Une formation`, tab: 1 },
  skills: { label: $localize`Trois compétences`, tab: 2 },
  language: { label: $localize`Une langue`, tab: 2 },
  resume: { label: $localize`Un CV`, tab: 4 },
};

/** Taille lisible : « 182 Ko », « 1,4 Mo ». */
export function formatSize(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} Ko`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} Mo`;
}

import { CandidateStatus } from '../../../../shared/models/application';
import { InterviewStatus, InterviewType } from '../../../../shared/models/interview';

/** Candidature vue par son auteur (docs/api.md §5) : aucun statut interne ni motif. */
export interface CandidateApplication {
  id: string;
  job: { id: string; title: string; companyName: string };
  candidateStatus: CandidateStatus;
  submittedAt: string;
  canWithdraw: boolean;
  /** Seulement dans le détail. */
  timeline?: { candidateStatus: CandidateStatus; at: string }[];
  /** Seulement dans le détail : résumés, sans lieu ni avis. */
  interviews?: {
    id: string;
    startsAt: string;
    durationMinutes: number;
    type: InterviewType;
    status: InterviewStatus;
  }[];
}

/** Critères du profil minimal (RG-09) renvoyés avec PROFILE_INCOMPLETE. */
export type ProfileGap = 'name' | 'headline' | 'skills' | 'resume';

/** Manque → libellé et onglet du profil où le combler (`/candidate/profile?tab=N`). */
export const PROFILE_GAPS: Record<ProfileGap, { label: string; tab: number }> = {
  name: { label: $localize`Prénom et nom`, tab: 0 },
  headline: { label: $localize`Titre professionnel`, tab: 0 },
  skills: { label: $localize`Au moins une compétence`, tab: 2 },
  resume: { label: $localize`Un CV`, tab: 4 },
};

/** Codes inconnus ignorés : un serveur plus récent ne casse pas l'écran. */
export function profileGaps(missing: unknown): { key: ProfileGap; label: string; tab: number }[] {
  if (!Array.isArray(missing)) {
    return [];
  }
  return missing
    .filter((key): key is ProfileGap => typeof key === 'string' && key in PROFILE_GAPS)
    .map((key) => ({ key, ...PROFILE_GAPS[key] }));
}

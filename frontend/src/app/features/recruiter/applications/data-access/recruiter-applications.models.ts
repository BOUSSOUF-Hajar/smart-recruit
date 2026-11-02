import { ApplicationStatus, RejectionReason } from '../../../../shared/models/application';
import { SkillLevel } from '../../../../shared/models/job';

/** Élément de la liste recruteur (docs/api.md §7). */
export interface RecruiterApplicationItem {
  id: string;
  candidate: { id: string; fullName: string; headline: string | null };
  job: { id: string; title: string };
  status: ApplicationStatus;
  submittedAt: string;
  lastMatchScore: number | null;
}

export interface DossierCandidate {
  id: string;
  fullName: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  city: string | null;
  countryCode: string | null;
  headline: string | null;
  summary: string | null;
  yearsOfExperience: number | null;
  experiences: {
    jobTitle: string;
    companyName: string;
    city: string | null;
    startDate: string;
    endDate: string | null;
    description: string | null;
  }[];
  educations: {
    degree: string;
    school: string;
    fieldOfStudy: string | null;
    startYear: number | null;
    endYear: number | null;
  }[];
  skills: { skillId: string; name: string; level: SkillLevel; years: number | null }[];
  languages: { languageCode: string; level: string }[];
  links: { type: string; url: string }[];
}

/** Dossier de candidature (US-31). `allowedTransitions` vient du serveur : rien n'est recalculé ici. */
export interface Dossier {
  id: string;
  status: ApplicationStatus;
  allowedTransitions: ApplicationStatus[];
  submittedAt: string;
  statusChangedAt: string;
  job: { id: string; title: string };
  candidate: DossierCandidate;
  resume: { id: string; originalFilename: string; sizeBytes: number; uploadedAt: string } | null;
  rejection: { reason: RejectionReason; comment: string | null } | null;
  withdrawalComment: string | null;
  interviews: {
    id: string;
    startsAt: string;
    durationMinutes: number;
    type: string;
    status: string;
  }[];
  notesCount: number;
  ai: { lastMatchScore: number | null; lastMatchedAt: string | null };
  version: number;
}

export type HistoryEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'NOTE_ADDED'
  | 'INTERVIEW_SCHEDULED'
  | 'INTERVIEW_RESCHEDULED'
  | 'INTERVIEW_CANCELLED'
  | 'INTERVIEW_COMPLETED'
  | 'AI_ANALYSIS_REQUESTED';

export interface HistoryEntry {
  id: string;
  type: HistoryEventType;
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus | null;
  actor: { id: string; name: string | null } | null;
  details: { rejectionReason?: RejectionReason; comment?: string } & Record<string, unknown>;
  occurredAt: string;
}

export interface ChangeStatusRequest {
  targetStatus: ApplicationStatus;
  rejectionReason: RejectionReason | null;
  comment: string | null;
  version: number;
}

/** Filtres de la liste, tels qu'ils vivent dans l'URL. `skillName` ne sert qu'à l'affichage. */
export interface ApplicationFilters {
  jobOfferId?: string;
  status?: string;
  skillId?: string;
  skillName?: string;
  candidateName?: string;
  submittedFrom?: string;
  submittedTo?: string;
  page?: string;
}

/** Paramètres HTTP à partir de l'URL : valeurs vides omises, `skillName` jamais envoyé. */
export function applicationParams(filters: ApplicationFilters): Record<string, string> {
  const params: Record<string, string> = {};
  for (const [key, value] of Object.entries(filters)) {
    if (key !== 'skillName' && typeof value === 'string' && value.trim() !== '') {
      params[key] = value.trim();
    }
  }
  return params;
}

/** Libellé d'une entrée d'historique, du point de vue du recruteur. */
export const HISTORY_EVENT_LABELS: Record<HistoryEventType, string> = {
  CREATED: $localize`Candidature reçue`,
  STATUS_CHANGED: $localize`Statut modifié`,
  NOTE_ADDED: $localize`Note ajoutée`,
  INTERVIEW_SCHEDULED: $localize`Entretien planifié`,
  INTERVIEW_RESCHEDULED: $localize`Entretien reprogrammé`,
  INTERVIEW_CANCELLED: $localize`Entretien annulé`,
  INTERVIEW_COMPLETED: $localize`Entretien terminé`,
  AI_ANALYSIS_REQUESTED: $localize`Analyse IA demandée`,
};

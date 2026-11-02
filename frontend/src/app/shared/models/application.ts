import { StatusTone } from '../ui/status-chip/status-chip';

/** Statuts internes (vue recruteur) : table D4.1 du cahier des charges. */
export type ApplicationStatus =
  | 'SUBMITTED'
  | 'SCREENING'
  | 'SHORTLISTED'
  | 'INTERVIEW'
  | 'OFFER'
  | 'HIRED'
  | 'REJECTED'
  | 'WITHDRAWN';

/** Statut simplifié calculé par le serveur pour le candidat (D4.2). */
export type CandidateStatus =
  'SENT' | 'IN_REVIEW' | 'INTERVIEW' | 'OFFER_RECEIVED' | 'HIRED' | 'NOT_SELECTED' | 'WITHDRAWN';

export type RejectionReason =
  | 'PROFILE_MISMATCH'
  | 'INSUFFICIENT_EXPERIENCE'
  | 'POSITION_FILLED'
  | 'CANDIDATE_UNRESPONSIVE'
  | 'OTHER';

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  SUBMITTED: $localize`Reçue`,
  SCREENING: $localize`Présélection`,
  SHORTLISTED: $localize`Retenue pour la suite`,
  INTERVIEW: $localize`Entretien`,
  OFFER: $localize`Proposition`,
  HIRED: $localize`Embauché`,
  REJECTED: $localize`Rejetée`,
  WITHDRAWN: $localize`Retirée par le candidat`,
};

/** Libellé du bouton qui mène à ce statut, dans le dialogue de changement. */
export const APPLICATION_ACTION_LABELS: Record<ApplicationStatus, string> = {
  SUBMITTED: $localize`Remettre en reçue`,
  SCREENING: $localize`Passer en présélection`,
  SHORTLISTED: $localize`Retenir pour la suite`,
  INTERVIEW: $localize`Passer en entretien`,
  OFFER: $localize`Faire une proposition`,
  HIRED: $localize`Confirmer l'embauche`,
  REJECTED: $localize`Rejeter`,
  WITHDRAWN: $localize`Retirer`,
};

export const APPLICATION_STATUS_TONES: Record<ApplicationStatus, StatusTone> = {
  SUBMITTED: 'info',
  SCREENING: 'neutral',
  SHORTLISTED: 'neutral',
  INTERVIEW: 'warning',
  OFFER: 'warning',
  HIRED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
};

export const CANDIDATE_STATUS_LABELS: Record<CandidateStatus, string> = {
  SENT: $localize`Envoyée`,
  IN_REVIEW: $localize`En cours d'examen`,
  INTERVIEW: $localize`Entretien`,
  OFFER_RECEIVED: $localize`Proposition reçue`,
  HIRED: $localize`Retenue`,
  NOT_SELECTED: $localize`Non retenue`,
  WITHDRAWN: $localize`Retirée`,
};

export const CANDIDATE_STATUS_TONES: Record<CandidateStatus, StatusTone> = {
  SENT: 'info',
  IN_REVIEW: 'neutral',
  INTERVIEW: 'warning',
  OFFER_RECEIVED: 'warning',
  HIRED: 'success',
  NOT_SELECTED: 'neutral',
  WITHDRAWN: 'neutral',
};

export const REJECTION_REASON_LABELS: Record<RejectionReason, string> = {
  PROFILE_MISMATCH: $localize`Profil hors sujet`,
  INSUFFICIENT_EXPERIENCE: $localize`Expérience insuffisante`,
  POSITION_FILLED: $localize`Poste pourvu`,
  CANDIDATE_UNRESPONSIVE: $localize`Candidat injoignable`,
  OTHER: $localize`Autre`,
};

/** Ordre d'affichage des statuts dans les filtres : celui du processus. */
export const APPLICATION_STATUSES: readonly ApplicationStatus[] = [
  'SUBMITTED',
  'SCREENING',
  'SHORTLISTED',
  'INTERVIEW',
  'OFFER',
  'HIRED',
  'REJECTED',
  'WITHDRAWN',
];

/** Statuts définitifs (RG-16). */
export function isFinalStatus(status: ApplicationStatus): boolean {
  return status === 'HIRED' || status === 'REJECTED' || status === 'WITHDRAWN';
}

import { StatusTone } from '../ui/status-chip/status-chip';

export type InterviewType = 'PHONE' | 'VIDEO' | 'ON_SITE' | 'TECHNICAL';
export type InterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export type FeedbackRating = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

export const INTERVIEW_TYPE_LABELS: Record<InterviewType, string> = {
  PHONE: $localize`Téléphone`,
  VIDEO: $localize`Visioconférence`,
  ON_SITE: $localize`Sur place`,
  TECHNICAL: $localize`Test technique`,
};

export const INTERVIEW_STATUS_LABELS: Record<InterviewStatus, string> = {
  SCHEDULED: $localize`Planifié`,
  COMPLETED: $localize`Terminé`,
  CANCELLED: $localize`Annulé`,
};

export const INTERVIEW_STATUS_TONES: Record<InterviewStatus, StatusTone> = {
  SCHEDULED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'neutral',
};

export const FEEDBACK_RATING_LABELS: Record<FeedbackRating, string> = {
  POSITIVE: $localize`Positif`,
  NEUTRAL: $localize`Mitigé`,
  NEGATIVE: $localize`Négatif`,
};

/**
 * RG-32 : valeur d'un `<input type="datetime-local">` (heure locale du navigateur, sans fuseau)
 * → instant ISO en UTC, tel que l'API l'attend. `null` si la saisie est vide ou invalide.
 */
export function localInputToIso(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value); // sans fuseau explicite : interprété dans le fuseau local
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/** Inverse : instant ISO (UTC) → valeur `yyyy-MM-ddTHH:mm` dans le fuseau local, pour pré-remplir un champ. */
export function isoToLocalInput(iso: string): string {
  const date = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

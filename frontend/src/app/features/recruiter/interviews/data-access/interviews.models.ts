import {
  FeedbackRating,
  InterviewStatus,
  InterviewType,
} from '../../../../shared/models/interview';

export interface Person {
  id: string;
  name: string;
}

/** Vue recruteur d'un entretien (docs/api.md §8), avis et participants compris. */
export interface RecruiterInterview {
  id: string;
  application: { id: string; candidateName: string; jobOfferId: string; jobTitle: string };
  startsAt: string;
  durationMinutes: number;
  type: InterviewType;
  meetingUrl: string | null;
  address: string | null;
  candidateInstructions: string | null;
  status: InterviewStatus;
  cancellationReason: string | null;
  feedback: { rating: FeedbackRating; comment: string | null; completedAt: string } | null;
  participants: Person[];
  scheduledBy: Person;
  version: number;
}

export interface InterviewRequest {
  startsAt: string;
  durationMinutes: number;
  type: InterviewType;
  meetingUrl: string | null;
  address: string | null;
  candidateInstructions: string | null;
  participantIds: string[];
  version?: number;
}

export interface CompanyRecruiter {
  id: string;
  name: string;
  jobTitle: string | null;
}

/** Fin d'un entretien, pour savoir s'il peut être clôturé (après son heure de début, F-INT-04). */
export function hasStarted(
  interview: Pick<RecruiterInterview, 'startsAt'>,
  now = new Date(),
): boolean {
  return new Date(interview.startsAt).getTime() <= now.getTime();
}

/** Bornes [lundi 0 h, lundi suivant 0 h[ de la semaine locale contenant `day`, en ISO UTC. */
export function weekRange(day: Date): { from: string; to: string } {
  const start = new Date(day.getFullYear(), day.getMonth(), day.getDate());
  const shift = (start.getDay() + 6) % 7; // lundi = 0
  start.setDate(start.getDate() - shift);
  const end = new Date(start);
  end.setDate(start.getDate() + 7);
  return { from: start.toISOString(), to: end.toISOString() };
}

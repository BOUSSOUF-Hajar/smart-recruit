import { ApplicationStatus } from '../../../shared/models/application';
import { JobStatus } from '../../../shared/models/job';

/** Tableau de bord recruteur (docs/api.md §10). */
export interface RecruiterDashboard {
  jobsByStatus: Partial<Record<JobStatus, number>>;
  applicationsReceived: { last7Days: number; last30Days: number };
  inScreening: number;
  shortlisted: number;
  upcomingInterviews: {
    id: string;
    applicationId: string;
    startsAt: string;
    type: string;
    candidateName: string;
    jobTitle: string;
  }[];
  hires: { last30Days: number };
  perJob: {
    jobId: string;
    title: string;
    status: JobStatus;
    countsByStatus: Partial<Record<ApplicationStatus, number>>;
  }[];
}

/** Date locale (yyyy-MM-dd) d'il y a `days` jours : début de période des listes filtrées. */
export function daysAgo(days: number, today = new Date()): string {
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Statuts montrés en colonnes du tableau par offre, dans l'ordre du processus. */
export const PIPELINE: readonly ApplicationStatus[] = [
  'SUBMITTED',
  'SCREENING',
  'SHORTLISTED',
  'INTERVIEW',
  'OFFER',
  'HIRED',
];

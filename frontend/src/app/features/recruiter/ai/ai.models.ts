import { SkillLevel } from '../../../shared/models/job';

export type AiTaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
export type AiTaskType = 'RESUME_ANALYSIS' | 'MATCHING';
export type AiErrorCode = 'AI_UNAVAILABLE' | 'AI_INVALID_RESPONSE' | 'FILE_UNREADABLE';
export type MatchLabel = 'STRONG' | 'PARTIAL' | 'WEAK';

export interface AiStatus {
  enabled: boolean;
  model: string | null;
}

export interface AiTask {
  id: string;
  type: AiTaskType;
  applicationId: string;
  status: AiTaskStatus;
  errorCode: AiErrorCode | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface ResumeAnalysis {
  skills: {
    name: string;
    evidence: string;
    skillId: string | null;
    referenceName: string | null;
  }[];
  experiences: {
    title: string;
    company: string | null;
    startYear: number | null;
    endYear: number | null;
    durationMonths: number | null;
  }[];
  educations: { degree: string; school: string | null; year: number | null }[];
  languages: { language: string; level: string | null }[];
  summary: string;
  model: { name: string; promptVersion: string };
  createdAt: string;
  disclaimer: string;
}

export interface MatchedSkill {
  skillId: string;
  name: string;
  source: 'PROFILE' | 'RESUME';
  level: SkillLevel | null;
  evidence: string | null;
  required: boolean;
  belowRequiredLevel: boolean;
}

export interface MatchResult {
  score: number;
  label: MatchLabel;
  breakdown: {
    requiredCoverage: number;
    preferredCoverage: number | null;
    experienceFit: number;
    formulaVersion: string;
  };
  matchedSkills: MatchedSkill[];
  missingSkills: { skillId: string; name: string; required: boolean }[];
  strengths: string[];
  weaknesses: string[];
  summary: string;
  warnings: string[];
  disclaimer: string;
  model: { name: string; promptVersion: string };
  createdAt: string;
}

export const MATCH_LABELS: Record<MatchLabel, string> = {
  STRONG: $localize`Forte adéquation`,
  PARTIAL: $localize`Adéquation partielle`,
  WEAK: $localize`Faible adéquation`,
};

export const AI_ERROR_MESSAGES: Record<AiErrorCode, string> = {
  AI_UNAVAILABLE: $localize`Analyse indisponible, réessayez plus tard.`,
  AI_INVALID_RESPONSE: $localize`Le modèle a renvoyé une réponse inexploitable. Réessayez plus tard.`,
  FILE_UNREADABLE: $localize`CV non analysable automatiquement (document scanné ?). Le dossier reste traitable manuellement.`,
};

export function isActive(task: Pick<AiTask, 'status'>): boolean {
  return task.status === 'PENDING' || task.status === 'RUNNING';
}

/** 0,333 → « 33 % » */
export function percent(value: number): string {
  return `${Math.round(value * 100)} %`;
}

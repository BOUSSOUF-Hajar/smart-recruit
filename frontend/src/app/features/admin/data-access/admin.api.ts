import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Role } from '../../../core/auth/auth.models';
import { JobStatus } from '../../../shared/models/job';

export interface Company {
  id: string;
  name: string;
  sector: string | null;
  sizeRange: string | null;
  city: string | null;
  countryCode: string | null;
  website: string | null;
  description: string | null;
  timezone: string;
  active: boolean;
  recruiterCount: number;
  /** Offres non archivées. */
  jobCount: number;
  publishedJobCount: number;
  version: number;
}

export interface CompanyRequest {
  name: string;
  sector: string | null;
  sizeRange: string | null;
  city: string | null;
  countryCode: string | null;
  website: string | null;
}

/** RG-31 : ce que la désactivation a fait, pour le message de confirmation. */
export interface CompanyDeactivation {
  company: Company;
  closedJobs: number;
  disabledRecruiters: number;
}

export type StaffRole = 'RECRUITER' | 'ADMIN';

export interface CreateStaffRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: StaffRole;
  companyId: string | null;
  jobTitle: string | null;
}

export interface CreatedStaff {
  user: {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    role: StaffRole;
    companyId: string | null;
    enabled: boolean;
    mustChangePassword: boolean;
  };
  /** Affiché une seule fois : jamais stocké en clair ni renvoyé par l'API ensuite. */
  temporaryPassword: string;
}

/** Compte vu par l'admin (US-48). */
export interface AdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: Role;
  enabled: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  companyId: string | null;
  companyName: string | null;
  jobTitle: string | null;
  version: number;
}

export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
  companyId: string | null;
  jobTitle: string | null;
  version: number;
}

export interface AdminCategory {
  id: string;
  name: string;
  active: boolean;
  offerCount: number;
}

export interface AdminSkill {
  id: string;
  name: string;
  active: boolean;
  /** Profils et offres qui l'utilisent. */
  usageCount: number;
  aliases: string[];
}

export interface SkillMerge {
  target: AdminSkill;
  references: number;
}

export type AuditResult = 'SUCCESS' | 'FAILURE';

export interface AuditEntry {
  id: string;
  occurredAt: string;
  actorId: string | null;
  actorEmail: string | null;
  actorName: string | null;
  action: string;
  resourceType: string | null;
  resourceId: string | null;
  result: AuditResult;
  ipAddress: string | null;
  correlationId: string | null;
  details: Record<string, unknown> | null;
}

export interface Counts {
  active: number;
  disabled: number;
}

export type AiState = 'AVAILABLE' | 'UNAVAILABLE' | 'DISABLED';

/** Tableau de bord admin (US-46). */
export interface AdminDashboard {
  usersByRole: Record<Role, Counts>;
  companies: Counts;
  jobsByStatus: Record<JobStatus, number>;
  applications: { total: number; last30Days: number };
  ai: { status: AiState; model: string | null };
}

const ADMIN = '/api/v1/admin';

/** Endpoints `/admin/**` : comptes, entreprises, référentiels (les lectures passent par httpResource). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  createCompany(request: CompanyRequest): Observable<Company> {
    return this.http.post<Company>(`${ADMIN}/companies`, request);
  }

  updateCompany(id: string, request: CompanyRequest, version: number): Observable<Company> {
    return this.http.put<Company>(`${ADMIN}/companies/${id}`, { ...request, version });
  }

  deactivateCompany(id: string): Observable<CompanyDeactivation> {
    return this.http.post<CompanyDeactivation>(`${ADMIN}/companies/${id}/deactivate`, null);
  }

  activateCompany(id: string): Observable<Company> {
    return this.http.post<Company>(`${ADMIN}/companies/${id}/activate`, null);
  }

  createStaff(request: CreateStaffRequest): Observable<CreatedStaff> {
    return this.http.post<CreatedStaff>(`${ADMIN}/users`, request);
  }

  updateUser(id: string, request: UpdateUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${ADMIN}/users/${id}`, request);
  }

  setUserEnabled(id: string, enabled: boolean): Observable<AdminUser> {
    return this.http.post<AdminUser>(
      `${ADMIN}/users/${id}/${enabled ? 'enable' : 'disable'}`,
      null,
    );
  }

  createCategory(name: string): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(`${ADMIN}/categories`, { name });
  }

  renameCategory(id: string, name: string): Observable<AdminCategory> {
    return this.http.put<AdminCategory>(`${ADMIN}/categories/${id}`, { name });
  }

  setCategoryActive(id: string, active: boolean): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(
      `${ADMIN}/categories/${id}/${active ? 'activate' : 'deactivate'}`,
      null,
    );
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${ADMIN}/categories/${id}`);
  }

  renameSkill(id: string, name: string): Observable<AdminSkill> {
    return this.http.put<AdminSkill>(`${ADMIN}/skills/${id}`, { name });
  }

  mergeSkill(sourceId: string, targetSkillId: string): Observable<SkillMerge> {
    return this.http.post<SkillMerge>(`${ADMIN}/skills/${sourceId}/merge`, { targetSkillId });
  }

  setSkillActive(id: string, active: boolean): Observable<AdminSkill> {
    return this.http.post<AdminSkill>(
      `${ADMIN}/skills/${id}/${active ? 'activate' : 'deactivate'}`,
      null,
    );
  }
}

export const COMPANY_SIZES = ['1-10', '11-50', '51-200', '201-1000', '1000+'] as const;

export const ROLE_LABELS: Record<Role, string> = {
  CANDIDATE: $localize`Candidat`,
  RECRUITER: $localize`Recruteur`,
  ADMIN: $localize`Administrateur`,
};

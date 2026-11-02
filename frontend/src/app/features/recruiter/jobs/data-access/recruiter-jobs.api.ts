import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Page } from '../../../../shared/models/job';
import { JobRequest, RecruiterJob, RecruiterJobSummary } from './recruiter-jobs.models';

/** Offres de l'entreprise du recruteur ; l'entreprise vient du token, jamais de la requête. */
@Injectable({ providedIn: 'root' })
export class RecruiterJobsApi {
  private readonly http = inject(HttpClient);

  list(status: string | undefined, page: number): Observable<Page<RecruiterJobSummary>> {
    const params: Record<string, string | number> = { page };
    if (status) {
      params['status'] = status;
    }
    return this.http.get<Page<RecruiterJobSummary>>('/api/v1/company/jobs', { params });
  }

  get(id: string): Observable<RecruiterJob> {
    return this.http.get<RecruiterJob>(`/api/v1/company/jobs/${id}`);
  }

  create(request: JobRequest): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>('/api/v1/jobs', request);
  }

  update(id: string, request: JobRequest): Observable<RecruiterJob> {
    return this.http.put<RecruiterJob>(`/api/v1/jobs/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/jobs/${id}`);
  }

  /** Publier ou rouvrir : date d'expiration au format `YYYY-MM-DD`. */
  publish(id: string, expiryDate: string): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>(`/api/v1/jobs/${id}/publish`, { expiryDate });
  }

  reopen(id: string, expiryDate: string): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>(`/api/v1/jobs/${id}/reopen`, { expiryDate });
  }

  unpublish(id: string): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>(`/api/v1/jobs/${id}/unpublish`, null);
  }

  close(id: string): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>(`/api/v1/jobs/${id}/close`, null);
  }

  archive(id: string): Observable<RecruiterJob> {
    return this.http.post<RecruiterJob>(`/api/v1/jobs/${id}/archive`, null);
  }
}

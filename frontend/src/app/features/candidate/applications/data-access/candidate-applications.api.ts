import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CandidateApplication } from './candidate-applications.models';

/** Écritures du candidat sur ses candidatures ; les lectures passent par httpResource dans les pages. */
@Injectable({ providedIn: 'root' })
export class CandidateApplicationsApi {
  private readonly http = inject(HttpClient);

  /** US-27 : corps vide, le CV actif est figé par le serveur. */
  apply(jobId: string): Observable<CandidateApplication> {
    return this.http.post<CandidateApplication>(`/api/v1/jobs/${jobId}/applications`, null);
  }

  /** US-29 */
  withdraw(id: string, comment: string | null): Observable<CandidateApplication> {
    return this.http.post<CandidateApplication>(
      `/api/v1/candidates/me/applications/${id}/withdraw`,
      { comment },
    );
  }
}

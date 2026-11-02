import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangeStatusRequest, Dossier } from './recruiter-applications.models';

/** Écritures et téléchargements du recruteur ; les lectures passent par httpResource dans les pages. */
@Injectable({ providedIn: 'root' })
export class RecruiterApplicationsApi {
  private readonly http = inject(HttpClient);

  /** US-32, US-33 : renvoie le dossier relu, avec la nouvelle version et les transitions suivantes. */
  changeStatus(id: string, request: ChangeStatusRequest): Observable<Dossier> {
    return this.http.patch<Dossier>(`/api/v1/applications/${id}/status`, request);
  }

  /** US-31 : CV figé à l'envoi ; le téléchargement est audité par le serveur. */
  downloadResume(id: string): Observable<Blob> {
    return this.http.get(`/api/v1/applications/${id}/resume`, { responseType: 'blob' });
  }
}

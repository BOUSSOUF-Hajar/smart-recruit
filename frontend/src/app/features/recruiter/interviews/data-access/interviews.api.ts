import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FeedbackRating } from '../../../../shared/models/interview';
import { InterviewRequest, RecruiterInterview } from './interviews.models';

/** Écritures sur les entretiens (US-35 à US-37) ; les lectures passent par httpResource. */
@Injectable({ providedIn: 'root' })
export class InterviewsApi {
  private readonly http = inject(HttpClient);

  schedule(applicationId: string, request: InterviewRequest): Observable<RecruiterInterview> {
    return this.http.post<RecruiterInterview>(
      `/api/v1/applications/${applicationId}/interviews`,
      request,
    );
  }

  reschedule(id: string, request: InterviewRequest): Observable<RecruiterInterview> {
    return this.http.put<RecruiterInterview>(`/api/v1/interviews/${id}`, request);
  }

  cancel(id: string, reason: string | null): Observable<RecruiterInterview> {
    return this.http.post<RecruiterInterview>(`/api/v1/interviews/${id}/cancel`, { reason });
  }

  complete(
    id: string,
    rating: FeedbackRating,
    comment: string | null,
  ): Observable<RecruiterInterview> {
    return this.http.post<RecruiterInterview>(`/api/v1/interviews/${id}/complete`, {
      rating,
      comment,
    });
  }
}

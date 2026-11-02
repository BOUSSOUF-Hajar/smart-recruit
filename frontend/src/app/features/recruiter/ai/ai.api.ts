import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, switchMap, takeWhile, timer } from 'rxjs';
import { AiTask, isActive } from './ai.models';

/** Intervalle de suivi d'une tâche (docs/api.md §9). */
export const POLL_INTERVAL_MS = 3000;

/** Demandes IA et suivi des tâches (US-41, US-42). */
@Injectable({ providedIn: 'root' })
export class AiApi {
  private readonly http = inject(HttpClient);

  analyse(applicationId: string): Observable<AiTask> {
    return this.http.post<AiTask>(`/api/v1/applications/${applicationId}/ai/resume-analysis`, null);
  }

  match(applicationId: string): Observable<AiTask> {
    return this.http.post<AiTask>(`/api/v1/applications/${applicationId}/ai/matching`, null);
  }

  /**
   * État de la tâche toutes les 3 s, jusqu'à DONE ou FAILED (dernier état émis compris).
   * `switchMap` abandonne une requête restée sans réponse au tick suivant : pas d'empilement.
   */
  watch(taskId: string, interval = POLL_INTERVAL_MS): Observable<AiTask> {
    return timer(0, interval).pipe(
      switchMap(() => this.http.get<AiTask>(`/api/v1/ai/tasks/${taskId}`)),
      takeWhile((task) => isActive(task), true),
    );
  }
}

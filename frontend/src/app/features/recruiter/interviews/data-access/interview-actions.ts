import { Injectable, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { EMPTY, Observable, catchError, filter, switchMap, tap } from 'rxjs';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { CancelDialogResult, CancelInterviewDialog } from '../components/cancel-dialog';
import { CompleteDialog, CompleteDialogResult } from '../components/complete-dialog';
import { InterviewDialog, InterviewDialogData } from '../components/interview-dialog';
import { InterviewsApi } from './interviews.api';
import { RecruiterInterview } from './interviews.models';

/**
 * Actions sur un entretien, communes au dossier de candidature et à l'agenda : chacune ouvre son
 * dialogue, appelle l'API et émet l'entretien à jour (rien si l'utilisateur renonce).
 */
@Injectable({ providedIn: 'root' })
export class InterviewActions {
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(InterviewsApi);
  private readonly notifications = inject(NotificationService);

  schedule(applicationId: string): Observable<RecruiterInterview> {
    return this.openEditor({ applicationId }).pipe(
      tap(() => this.notifications.success($localize`Entretien planifié.`)),
    );
  }

  reschedule(interview: RecruiterInterview): Observable<RecruiterInterview> {
    return this.openEditor({ applicationId: interview.application.id, interview }).pipe(
      tap(() => this.notifications.success($localize`Entretien mis à jour.`)),
    );
  }

  cancel(interview: RecruiterInterview): Observable<RecruiterInterview> {
    return this.dialog
      .open<CancelInterviewDialog, void, CancelDialogResult>(CancelInterviewDialog, {
        width: '440px',
      })
      .afterClosed()
      .pipe(
        filter((result) => !!result),
        switchMap((result) => this.handle(this.api.cancel(interview.id, result!.reason))),
        tap(() => this.notifications.success($localize`Entretien annulé.`)),
      );
  }

  complete(interview: RecruiterInterview): Observable<RecruiterInterview> {
    return this.dialog
      .open<CompleteDialog, void, CompleteDialogResult>(CompleteDialog, { width: '480px' })
      .afterClosed()
      .pipe(
        filter((result) => !!result),
        switchMap((result) =>
          this.handle(this.api.complete(interview.id, result!.rating, result!.comment)),
        ),
        tap(() => this.notifications.success($localize`Entretien clôturé.`)),
      );
  }

  private openEditor(data: InterviewDialogData): Observable<RecruiterInterview> {
    return this.dialog
      .open<InterviewDialog, InterviewDialogData, RecruiterInterview>(InterviewDialog, {
        data,
        width: '560px',
      })
      .afterClosed()
      .pipe(filter((saved): saved is RecruiterInterview => !!saved));
  }

  /** INVALID_STATE, ACCESS_DENIED… : le serveur explique, on affiche et on n'émet rien. */
  private handle(call: Observable<RecruiterInterview>): Observable<RecruiterInterview> {
    return call.pipe(
      catchError((error: unknown) => {
        if (isApiError(error)) {
          this.notifications.error(error.detail ?? error.title);
        }
        return EMPTY;
      }),
    );
  }
}

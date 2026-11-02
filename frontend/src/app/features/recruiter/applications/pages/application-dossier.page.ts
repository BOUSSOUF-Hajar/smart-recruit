import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { filter, switchMap } from 'rxjs';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  REJECTION_REASON_LABELS,
  RejectionReason,
} from '../../../../shared/models/application';
import { SKILL_LEVEL_LABELS, SkillLevel } from '../../../../shared/models/job';
import { confirm } from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { EmptyState } from '../../../../shared/ui/empty-state/empty-state';
import {
  LANGUAGE_LEVEL_LABELS,
  LINK_TYPE_LABELS,
  formatSize,
  languageLabel,
} from '../../../candidate/profile/data-access/profile-labels';
import { LanguageLevel, LinkType } from '../../../candidate/profile/data-access/profile.models';
import { ApplicationStatusChip } from '../components/application-status';
import { AiPanel } from '../../ai/ai-panel';
import { InterviewsPanel } from '../components/interviews-panel';
import { NotesPanel } from '../components/notes-panel';
import { StatusDialog } from '../components/status-dialog';
import { RecruiterApplicationsApi } from '../data-access/recruiter-applications.api';
import {
  ChangeStatusRequest,
  Dossier,
  HISTORY_EVENT_LABELS,
  HistoryEntry,
} from '../data-access/recruiter-applications.models';

/**
 * US-31 à US-34, US-35 à US-39 : dossier de candidature, avec ses entretiens et ses notes. Le menu de statut ne propose que les transitions envoyées
 * par le serveur ; un conflit de version invite à recharger plutôt qu'à écraser.
 */
@Component({
  selector: 'app-application-dossier-page',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatTabsModule,
    EmptyState,
    ApplicationStatusChip,
    InterviewsPanel,
    NotesPanel,
    AiPanel,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './application-dossier.page.html',
  styleUrl: './application-dossier.page.scss',
})
export class ApplicationDossierPage {
  private readonly api = inject(RecruiterApplicationsApi);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  /** Paramètre de route `:id`. */
  readonly id = input.required<string>();

  protected readonly dossier = httpResource<Dossier>(() => `/api/v1/applications/${this.id()}`);
  protected readonly history = httpResource<HistoryEntry[]>(
    () => `/api/v1/applications/${this.id()}/history`,
  );
  protected readonly data = computed(() =>
    this.dossier.hasValue() ? this.dossier.value() : undefined,
  );
  protected readonly entries = computed(() =>
    this.history.hasValue() ? this.history.value() : [],
  );
  protected readonly notFound = computed(() => {
    const error = this.dossier.error();
    return isApiError(error) && error.status === 404;
  });

  protected readonly saving = signal(false);
  protected readonly downloading = signal(false);
  /** Quelqu'un d'autre a modifié la candidature depuis son affichage (US-32). */
  protected readonly conflict = signal(false);

  protected statusLabel(status: ApplicationStatus | null): string {
    return status ? APPLICATION_STATUS_LABELS[status] : '';
  }

  protected reasonLabel(reason: RejectionReason | undefined): string {
    return reason ? REJECTION_REASON_LABELS[reason] : '';
  }

  protected eventLabel(entry: HistoryEntry): string {
    return HISTORY_EVENT_LABELS[entry.type];
  }

  protected skillLevel(level: SkillLevel): string {
    return SKILL_LEVEL_LABELS[level];
  }

  protected language(code: string, level: string): string {
    return `${languageLabel(code)} · ${LANGUAGE_LEVEL_LABELS[level as LanguageLevel] ?? level}`;
  }

  protected linkType(type: string): string {
    return LINK_TYPE_LABELS[type as LinkType] ?? type;
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  /** Un entretien ou une note a changé : transitions (RG-33), compteurs et historique se mettent à jour. */
  protected refresh(): void {
    this.dossier.reload();
    this.history.reload();
  }

  protected reload(): void {
    this.conflict.set(false);
    this.dossier.reload();
    this.history.reload();
  }

  protected changeStatus(dossier: Dossier): void {
    this.dialog
      .open<StatusDialog, Dossier, ChangeStatusRequest>(StatusDialog, {
        data: dossier,
        width: '520px',
      })
      .afterClosed()
      .pipe(
        filter((request): request is ChangeStatusRequest => !!request),
        // US-33 : le rejet est définitif, on le confirme une seconde fois
        switchMap((request) =>
          request.targetStatus !== 'REJECTED'
            ? [request]
            : confirm(this.dialog, {
                title: $localize`Rejeter la candidature ?`,
                message: $localize`Le rejet est définitif. Le candidat verra « Non retenue », sans le motif ni votre commentaire.`,
                confirmLabel: $localize`Rejeter`,
              }).pipe(
                filter(Boolean),
                switchMap(() => [request]),
              ),
        ),
      )
      .subscribe((request) => this.send(dossier.id, request));
  }

  protected downloadResume(dossier: Dossier): void {
    if (!dossier.resume) {
      return;
    }
    const filename = dossier.resume.originalFilename;
    this.downloading.set(true);
    this.api.downloadResume(dossier.id).subscribe({
      next: (blob) => {
        this.downloading.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.downloading.set(false),
    });
  }

  private send(id: string, request: ChangeStatusRequest): void {
    this.saving.set(true);
    this.api.changeStatus(id, request).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.dossier.set(updated);
        this.history.reload();
        this.notifications.success(
          $localize`Candidature passée à « ${APPLICATION_STATUS_LABELS[updated.status]}:status: ».`,
        );
      },
      error: (error: unknown) => {
        this.saving.set(false);
        if (!isApiError(error)) {
          return;
        }
        switch (error.code) {
          case 'CONCURRENT_MODIFICATION':
            this.conflict.set(true);
            break;
          case 'INVALID_STATUS_TRANSITION':
          case 'OFFER_REQUIRES_COMPLETED_INTERVIEW':
            this.notifications.error(error.detail ?? error.title);
            this.reload();
            break;
          default:
            this.notifications.error(error.detail ?? error.title);
        }
      },
    });
  }
}

import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { confirm } from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { FileUpload } from '../../../../shared/ui/file-upload/file-upload';
import { CandidateProfileApi } from '../data-access/profile.api';
import { formatSize } from '../data-access/profile-labels';
import { ResumeInfo } from '../data-access/profile.models';

/** US-17, US-18, US-19 : CV actif, remplacement, téléchargement et suppression. */
@Component({
  selector: 'app-resume-panel',
  imports: [DatePipe, MatButtonModule, MatIconModule, FileUpload],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 i18n>CV</h2>
    @if (resume(); as current) {
      <div class="current">
        <mat-icon aria-hidden="true">description</mat-icon>
        <div class="details">
          <strong>{{ current.originalFilename }}</strong>
          <span class="meta"
            >{{ size(current.sizeBytes) }} · {{ current.uploadedAt | date: 'd MMM y, HH:mm' }}</span
          >
        </div>
        <button
          mat-stroked-button
          type="button"
          (click)="download(current)"
          [disabled]="downloading()"
        >
          <mat-icon>download</mat-icon>
          <span i18n>Télécharger</span>
        </button>
        <button mat-button type="button" (click)="remove()" [disabled]="deleting()">
          <mat-icon>delete</mat-icon>
          <span i18n>Supprimer</span>
        </button>
      </div>
      <p class="hint" i18n>
        Un nouveau fichier remplace ce CV pour vos prochaines candidatures ; celles déjà envoyées
        gardent le leur.
      </p>
    } @else {
      <p class="hint" i18n>Aucun CV pour l'instant : il est nécessaire pour postuler.</p>
    }

    <app-file-upload [busy]="uploading()" (fileSelected)="upload($event)" />
    @if (serverError(); as message) {
      <p class="error" role="alert">{{ message }}</p>
    }
  `,
  styles: `
    h2 {
      margin: 0 0 8px;
      font: var(--mat-sys-title-large);
    }
    .current {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .details {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
      overflow-wrap: anywhere;
    }
    .meta,
    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      margin: 8px 0 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class ResumePanel {
  private readonly api = inject(CandidateProfileApi);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  readonly resume = input<ResumeInfo | null>(null);
  readonly changed = output<void>();

  protected readonly uploading = signal(false);
  protected readonly downloading = signal(false);
  protected readonly deleting = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  protected upload(file: File): void {
    this.serverError.set(null);
    this.uploading.set(true);
    this.api.uploadResume(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.notifications.success($localize`CV enregistré.`);
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.uploading.set(false);
        // FILE_TOO_LARGE, FILE_INVALID_TYPE, FILE_UNREADABLE : le serveur dit précisément pourquoi
        if (isApiError(error) && error.status >= 400 && error.status < 500 && error.detail) {
          this.serverError.set(error.detail);
        }
      },
    });
  }

  /**
   * US-19. Un CV déjà envoyé avec une candidature reste visible des recruteurs concernés (RG-12) :
   * on le dit avant, pour que le candidat ne croie pas l'avoir retiré de partout.
   */
  protected remove(): void {
    confirm(this.dialog, {
      title: $localize`Supprimer votre CV ?`,
      message: $localize`Il ne sera plus proposé pour vos prochaines candidatures. S'il a déjà été envoyé avec une candidature, les recruteurs concernés le gardent.`,
      confirmLabel: $localize`Supprimer`,
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.deleting.set(true);
      this.api.deleteResume().subscribe({
        next: () => {
          this.deleting.set(false);
          this.notifications.success($localize`CV supprimé.`);
          this.changed.emit();
        },
        error: () => this.deleting.set(false),
      });
    });
  }

  /** Téléchargement par HttpClient (token compris), puis enregistrement local du fichier reçu. */
  protected download(resume: ResumeInfo): void {
    this.downloading.set(true);
    this.api.downloadResume().subscribe({
      next: (blob) => {
        this.downloading.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = resume.originalFilename;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.downloading.set(false),
    });
  }
}

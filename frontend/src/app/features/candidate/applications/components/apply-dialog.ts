import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { httpResource } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { CandidateProfile } from '../../profile/data-access/profile.models';
import { CandidateApplicationsApi } from '../data-access/candidate-applications.api';
import { profileGaps } from '../data-access/candidate-applications.models';

export interface ApplyDialogData {
  jobId: string;
  jobTitle: string;
  companyName: string;
}

/** Résultat : identifiant de la candidature créée ou existante ; `undefined` si annulé. */
export type ApplyDialogResult = { applicationId: string; created: boolean } | undefined;

/**
 * US-27 : récapitulatif de ce qui sera transmis (F-APP-01, étape 2), puis confirmation. Le serveur
 * reste seul juge : profil incomplet, offre clôturée ou candidature existante sont expliqués ici.
 */
@Component({
  selector: 'app-apply-dialog',
  imports: [RouterLink, MatButtonModule, MatDialogModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Postuler</h2>
    <mat-dialog-content>
      <p class="job">
        <strong>{{ data.jobTitle }}</strong> · {{ data.companyName }}
      </p>

      @if (profile.isLoading()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (summary(); as p) {
        <p i18n>Le recruteur recevra :</p>
        <ul class="recap">
          <li>
            <mat-icon aria-hidden="true">badge</mat-icon>
            <span
              >{{ p.firstName }} {{ p.lastName }}
              @if (p.headline) {
                — {{ p.headline }}
              }
            </span>
          </li>
          <li>
            <mat-icon aria-hidden="true">psychology</mat-icon>
            <span i18n
              >Votre profil complet : parcours, {{ p.skills.length }} compétence(s), langues,
              liens</span
            >
          </li>
          <li>
            <mat-icon aria-hidden="true">description</mat-icon>
            @if (p.resume) {
              <span i18n>CV : {{ p.resume.originalFilename }}</span>
            } @else {
              <span i18n>Aucun CV</span>
            }
          </li>
        </ul>
        <p class="hint" i18n>
          Cette version de votre CV restera attachée à la candidature, même si vous la remplacez
          ensuite.
        </p>
      }

      @if (gaps().length) {
        <div class="problem" role="alert">
          <p i18n>Complétez votre profil avant de postuler :</p>
          <ul>
            @for (gap of gaps(); track gap.key) {
              <li>
                <a
                  [routerLink]="['/candidate/profile']"
                  [queryParams]="{ tab: gap.tab }"
                  (click)="dialogRef.close()"
                  >{{ gap.label }}</a
                >
              </li>
            }
          </ul>
        </div>
      }
      @if (problem(); as message) {
        <p class="problem" role="alert">{{ message }}</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
      <button
        mat-flat-button
        type="button"
        (click)="confirm()"
        [disabled]="sending() || closed() || !summary()"
        i18n
      >
        Confirmer ma candidature
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .job {
      margin-top: 0;
    }
    .recap {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 0;
      list-style: none;
    }
    .recap li {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .hint {
      color: var(--mat-sys-on-surface-variant);
    }
    .problem {
      color: var(--mat-sys-error);
    }
  `,
})
export class ApplyDialog {
  private readonly api = inject(CandidateApplicationsApi);
  private readonly notifications = inject(NotificationService);
  protected readonly dialogRef = inject<MatDialogRef<ApplyDialog, ApplyDialogResult>>(MatDialogRef);
  protected readonly data = inject<ApplyDialogData>(MAT_DIALOG_DATA);

  protected readonly profile = httpResource<CandidateProfile>(
    () => '/api/v1/candidates/me/profile',
  );
  protected readonly summary = computed(() =>
    this.profile.hasValue() ? this.profile.value() : undefined,
  );

  protected readonly sending = signal(false);
  /** L'offre n'accepte plus de candidatures : inutile de réessayer. */
  protected readonly closed = signal(false);
  protected readonly gaps = signal<ReturnType<typeof profileGaps>>([]);
  protected readonly problem = signal<string | null>(null);

  protected confirm(): void {
    this.sending.set(true);
    this.problem.set(null);
    this.gaps.set([]);
    this.api.apply(this.data.jobId).subscribe({
      next: (application) => {
        this.notifications.success($localize`Candidature envoyée.`);
        this.dialogRef.close({ applicationId: application.id, created: true });
      },
      error: (error: unknown) => {
        this.sending.set(false);
        if (!isApiError(error)) {
          return;
        }
        switch (error.code) {
          case 'PROFILE_INCOMPLETE':
            this.gaps.set(profileGaps(error.extensions['missing']));
            break;
          case 'APPLICATION_ALREADY_EXISTS': {
            const existing = error.extensions['applicationId'];
            this.dialogRef.close(
              typeof existing === 'string'
                ? { applicationId: existing, created: false }
                : undefined,
            );
            break;
          }
          case 'JOB_NOT_OPEN':
            this.closed.set(true);
            this.problem.set(
              $localize`Cette offre vient d'être clôturée : elle n'accepte plus de candidatures.`,
            );
            break;
          case 'CONCURRENT_MODIFICATION':
            this.problem.set($localize`Votre CV vient de changer. Réessayez.`);
            this.profile.reload();
            break;
          default:
            this.problem.set(error.detail ?? error.title);
        }
      },
    });
  }
}

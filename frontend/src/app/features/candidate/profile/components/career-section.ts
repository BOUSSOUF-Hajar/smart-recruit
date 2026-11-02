import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { filter, switchMap } from 'rxjs';
import { NotificationService } from '../../../../core/errors/notification.service';
import { confirm } from '../../../../shared/ui/confirm-dialog/confirm-dialog';
import { CandidateProfileApi } from '../data-access/profile.api';
import { Education, Experience } from '../data-access/profile.models';
import { EducationDialog } from './education-dialog';
import { ExperienceDialog } from './experience-dialog';

/** US-14 : expériences et formations, de la plus récente à la plus ancienne (ordre du serveur). */
@Component({
  selector: 'app-career-section',
  imports: [DatePipe, MatButtonModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="experiences-title">
      <div class="section-header">
        <h2 id="experiences-title" i18n>Expériences</h2>
        <button mat-stroked-button type="button" (click)="editExperience(null)">
          <mat-icon>add</mat-icon>
          <span i18n>Ajouter</span>
        </button>
      </div>
      @for (experience of experiences(); track experience.id) {
        <article class="item">
          <div class="item-text">
            <h3>{{ experience.jobTitle }} · {{ experience.companyName }}</h3>
            <p class="meta">
              {{ experience.startDate | date: 'MMM yyyy' }} –
              @if (experience.current) {
                <ng-container i18n>aujourd'hui</ng-container>
              } @else {
                {{ experience.endDate | date: 'MMM yyyy' }}
              }
              @if (experience.city) {
                · {{ experience.city }}
              }
            </p>
            @if (experience.description) {
              <p class="description">{{ experience.description }}</p>
            }
          </div>
          <div class="item-actions">
            <button
              mat-icon-button
              type="button"
              (click)="editExperience(experience)"
              [attr.aria-label]="editLabel(experience.jobTitle)"
            >
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              (click)="deleteExperience(experience)"
              [attr.aria-label]="deleteLabel(experience.jobTitle)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        </article>
      } @empty {
        <p class="empty" i18n>Aucune expérience pour l'instant.</p>
      }
    </section>

    <section aria-labelledby="educations-title">
      <div class="section-header">
        <h2 id="educations-title" i18n>Formations</h2>
        <button mat-stroked-button type="button" (click)="editEducation(null)">
          <mat-icon>add</mat-icon>
          <span i18n>Ajouter</span>
        </button>
      </div>
      @for (education of educations(); track education.id) {
        <article class="item">
          <div class="item-text">
            <h3>{{ education.degree }} · {{ education.school }}</h3>
            <p class="meta">
              @if (education.fieldOfStudy) {
                {{ education.fieldOfStudy }} ·
              }
              {{ education.startYear ?? '…' }} – {{ education.endYear ?? enCours }}
            </p>
          </div>
          <div class="item-actions">
            <button
              mat-icon-button
              type="button"
              (click)="editEducation(education)"
              [attr.aria-label]="editLabel(education.degree)"
            >
              <mat-icon>edit</mat-icon>
            </button>
            <button
              mat-icon-button
              type="button"
              (click)="deleteEducation(education)"
              [attr.aria-label]="deleteLabel(education.degree)"
            >
              <mat-icon>delete</mat-icon>
            </button>
          </div>
        </article>
      } @empty {
        <p class="empty" i18n>Aucune formation pour l'instant.</p>
      }
    </section>
  `,
  styles: `
    section + section {
      margin-top: 32px;
    }
    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    h2 {
      margin: 0;
      font: var(--mat-sys-title-large);
    }
    .item {
      display: flex;
      justify-content: space-between;
      gap: 8px;
      padding: 12px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    h3 {
      margin: 0;
      font: var(--mat-sys-title-small);
    }
    .meta,
    .empty {
      margin: 4px 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .description {
      margin: 8px 0 0;
      white-space: pre-line;
    }
    .item-actions {
      display: flex;
      flex-shrink: 0;
    }
  `,
})
export class CareerSection {
  private readonly dialog = inject(MatDialog);
  private readonly api = inject(CandidateProfileApi);
  private readonly notifications = inject(NotificationService);

  readonly experiences = input.required<Experience[]>();
  readonly educations = input.required<Education[]>();
  /** Émis après chaque modification : la page recharge le profil (ordre, complétude). */
  readonly changed = output<void>();

  protected readonly enCours = $localize`en cours`;

  protected editLabel(name: string): string {
    return $localize`Modifier ${name}:name:`;
  }

  protected deleteLabel(name: string): string {
    return $localize`Supprimer ${name}:name:`;
  }

  protected editExperience(experience: Experience | null): void {
    this.dialog
      .open(ExperienceDialog, { data: experience, autoFocus: 'first-tabbable' })
      .afterClosed()
      .pipe(filter((saved) => saved !== undefined))
      .subscribe(() => this.changed.emit());
  }

  protected editEducation(education: Education | null): void {
    this.dialog
      .open(EducationDialog, { data: education, autoFocus: 'first-tabbable' })
      .afterClosed()
      .pipe(filter((saved) => saved !== undefined))
      .subscribe(() => this.changed.emit());
  }

  protected deleteExperience(experience: Experience): void {
    confirm(this.dialog, {
      title: $localize`Supprimer cette expérience ?`,
      message: `${experience.jobTitle} · ${experience.companyName}`,
      confirmLabel: $localize`Supprimer`,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.deleteExperience(experience.id)),
      )
      .subscribe(() => {
        this.notifications.success($localize`Expérience supprimée.`);
        this.changed.emit();
      });
  }

  protected deleteEducation(education: Education): void {
    confirm(this.dialog, {
      title: $localize`Supprimer cette formation ?`,
      message: `${education.degree} · ${education.school}`,
      confirmLabel: $localize`Supprimer`,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.deleteEducation(education.id)),
      )
      .subscribe(() => {
        this.notifications.success($localize`Formation supprimée.`);
        this.changed.emit();
      });
  }
}

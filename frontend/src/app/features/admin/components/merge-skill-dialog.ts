import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { NotificationService } from '../../../core/errors/notification.service';
import { SkillAutocomplete } from '../../../shared/ui/skill-autocomplete/skill-autocomplete';
import { SkillOption } from '../../../shared/ui/skill-autocomplete/skill-search';
import { notifyFailure } from '../data-access/admin-errors';
import { AdminApi, AdminSkill, SkillMerge } from '../data-access/admin.api';

/**
 * US-51 : fusion d'un doublon dans la compétence à garder. L'admin choisit la cible dans le
 * référentiel (sans création), relit ce qui va se passer, puis confirme.
 */
@Component({
  selector: 'app-merge-skill-dialog',
  imports: [MatButtonModule, MatDialogModule, SkillAutocomplete],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Fusionner « {{ source.name }} »</h2>
    <mat-dialog-content class="content">
      <p i18n>
        Choisissez la compétence à garder. « {{ source.name }} » disparaîtra et deviendra un alias
        de la cible.
      </p>
      <app-skill-autocomplete
        label="Compétence à garder"
        i18n-label
        [excludedIds]="[source.id]"
        [allowCreate]="false"
        (selected)="target.set($event)"
      />
      @if (target(); as chosen) {
        <p class="summary" role="status" i18n>
          {{ source.usageCount }} profil(s) et offre(s) passeront à « {{ chosen.name }} ». Un profil
          qui avait les deux garde le niveau le plus élevé. Cette action ne s'annule pas.
        </p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close i18n>Annuler</button>
      <button
        mat-flat-button
        type="button"
        [disabled]="!target() || saving()"
        (click)="merge()"
        i18n
      >
        Fusionner
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .content {
      min-width: min(460px, 80vw);
    }
    .summary {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class MergeSkillDialog {
  protected readonly source = inject<AdminSkill>(MAT_DIALOG_DATA);
  private readonly api = inject(AdminApi);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<MergeSkillDialog, SkillMerge>>(MatDialogRef);

  protected readonly target = signal<SkillOption | null>(null);
  protected readonly saving = signal(false);

  protected merge(): void {
    const target = this.target();
    if (!target) {
      return;
    }
    this.saving.set(true);
    this.api.mergeSkill(this.source.id, target.id).subscribe({
      next: (result) => this.dialogRef.close(result),
      error: (error: unknown) => {
        this.saving.set(false);
        notifyFailure(this.notifications, error);
      },
    });
  }
}

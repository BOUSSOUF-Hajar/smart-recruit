import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { CreatedStaff } from '../data-access/admin.api';

/**
 * US-47 : le mot de passe temporaire n'est montré qu'ici, une seule fois. L'admin le transmet au
 * nouvel utilisateur par un canal sûr ; il devra le changer à sa première connexion.
 */
@Component({
  selector: 'app-temporary-password-dialog',
  imports: [MatButtonModule, MatDialogModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 mat-dialog-title i18n>Compte créé</h2>
    <mat-dialog-content>
      <p i18n>
        Mot de passe temporaire de {{ data.user.firstName }} {{ data.user.lastName }} ({{
          data.user.email
        }}) :
      </p>
      <p class="password">
        <code>{{ data.temporaryPassword }}</code>
        <button
          mat-icon-button
          type="button"
          (click)="copy()"
          aria-label="Copier le mot de passe"
          i18n-aria-label
        >
          <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
        </button>
      </p>
      <p class="warning" role="note" i18n>
        Il ne sera plus jamais affiché. Transmettez-le par un canal sûr ; il devra être changé à la
        première connexion.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-flat-button type="button" mat-dialog-close i18n>
        J'ai transmis le mot de passe
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .password {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    code {
      padding: 8px 12px;
      border-radius: 8px;
      font-size: 1.25rem;
      letter-spacing: 0.05em;
      background: var(--mat-sys-surface-container-high);
    }
    .warning {
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class TemporaryPasswordDialog {
  private readonly clipboard = inject(Clipboard);
  protected readonly data = inject<CreatedStaff>(MAT_DIALOG_DATA);
  protected readonly copied = signal(false);

  protected copy(): void {
    this.copied.set(this.clipboard.copy(this.data.temporaryPassword));
  }
}

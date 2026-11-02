import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';

/**
 * Choix d'un fichier par glisser-déposer ou bouton, avec contrôle du type et de la taille avant
 * l'envoi (US-17). Contrôle de confort seulement : le serveur revérifie tout, signature comprise.
 */
@Component({
  selector: 'app-file-upload',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="drop-zone"
      [class.drop-zone--active]="dragging()"
      (dragover)="onDragOver($event)"
      (dragleave)="dragging.set(false)"
      (drop)="onDrop($event)"
    >
      <mat-icon aria-hidden="true">upload_file</mat-icon>
      <p class="instructions">{{ instructions() }}</p>
      <button mat-stroked-button type="button" [disabled]="busy()" (click)="picker.click()">
        {{ buttonLabel() }}
      </button>
      <input
        #picker
        class="picker"
        type="file"
        [accept]="accept()"
        (change)="onPicked(picker)"
        tabindex="-1"
        aria-hidden="true"
      />
    </div>
    @if (busy()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (error()) {
      <p class="error" role="alert">{{ error() }}</p>
    }
  `,
  styles: `
    .drop-zone {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 24px 16px;
      border: 2px dashed var(--mat-sys-outline-variant);
      border-radius: 12px;
      text-align: center;
    }
    .drop-zone--active {
      border-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
    .instructions {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .picker {
      display: none;
    }
    .error {
      margin: 8px 0 0;
      color: var(--mat-sys-error);
    }
  `,
})
export class FileUpload {
  /** Types MIME acceptés, séparés par des virgules (attribut `accept`). */
  readonly accept = input('application/pdf');
  /** Extension exigée en plus du type, le type annoncé par le navigateur pouvant être vide. */
  readonly extension = input('.pdf');
  readonly maxBytes = input(5 * 1024 * 1024);
  readonly busy = input(false);
  readonly instructions = input($localize`Glissez votre CV au format PDF ici (5 Mo maximum).`);
  readonly buttonLabel = input($localize`Choisir un fichier`);
  readonly fileSelected = output<File>();

  protected readonly dragging = signal(false);
  protected readonly error = signal<string | null>(null);

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files.item(0);
    if (file && !this.busy()) {
      this.check(file);
    }
  }

  protected onPicked(picker: HTMLInputElement): void {
    const file = picker.files?.item(0);
    picker.value = ''; // permet de choisir à nouveau le même fichier
    if (file) {
      this.check(file);
    }
  }

  /** Visible pour les tests : applique les contrôles puis émet le fichier s'il est acceptable. */
  check(file: File): void {
    const message = validateFile(file, this.accept(), this.extension(), this.maxBytes());
    this.error.set(message);
    if (!message) {
      this.fileSelected.emit(file);
    }
  }
}

/** Message d'erreur, ou `null` si le fichier peut être envoyé. */
export function validateFile(
  file: File,
  accept: string,
  extension: string,
  maxBytes: number,
): string | null {
  const types = accept.split(',').map((type) => type.trim());
  const typeOk = file.type === '' || types.includes(file.type);
  if (!typeOk || !file.name.toLowerCase().endsWith(extension)) {
    return $localize`Seuls les fichiers PDF sont acceptés.`;
  }
  if (file.size > maxBytes) {
    const megabytes = Math.round(maxBytes / (1024 * 1024));
    return $localize`Le fichier dépasse ${megabytes}:max: Mo.`;
  }
  if (file.size === 0) {
    return $localize`Le fichier est vide.`;
  }
  return null;
}

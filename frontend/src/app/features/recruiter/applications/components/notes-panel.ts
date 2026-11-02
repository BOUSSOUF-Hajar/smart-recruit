import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient, httpResource } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { isApiError } from '../../../../core/errors/api-error';
import { NotificationService } from '../../../../core/errors/notification.service';
import { confirm } from '../../../../shared/ui/confirm-dialog/confirm-dialog';

export interface ApplicationNote {
  id: string;
  content: string;
  author: { id: string; name: string };
  createdAt: string;
  updatedAt: string;
  edited: boolean;
  mine: boolean;
}

const MAX_LENGTH = 5000;

/**
 * Onglet « Notes » du dossier (US-39) : visibles de toute l'équipe, modifiables par leur seul
 * auteur ; jamais montrées au candidat (RG-23).
 */
@Component({
  selector: 'app-notes-panel',
  imports: [DatePipe, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="notes">
      @for (note of list(); track note.id) {
        <li class="note">
          <div class="meta">
            <strong>{{ note.author.name }}</strong>
            <span class="sub">{{ note.createdAt | date: 'd MMM y, HH:mm' }}</span>
            @if (note.edited) {
              <span class="sub" i18n
                >· modifiée le {{ note.updatedAt | date: 'd MMM y, HH:mm' }}</span
              >
            }
          </div>
          @if (editing() === note.id) {
            <mat-form-field class="field">
              <textarea matInput [formControl]="editor" rows="3" [maxlength]="max"></textarea>
            </mat-form-field>
            <div class="actions">
              <button mat-button type="button" (click)="editing.set(null)" i18n>Annuler</button>
              <button
                mat-flat-button
                type="button"
                (click)="saveEdit(note)"
                [disabled]="editor.invalid"
                i18n
              >
                Enregistrer
              </button>
            </div>
          } @else {
            <p class="content">{{ note.content }}</p>
            @if (note.mine) {
              <div class="actions">
                <button mat-button type="button" (click)="startEdit(note)" i18n>Modifier</button>
                <button mat-button type="button" (click)="remove(note)" i18n>Supprimer</button>
              </div>
            }
          }
        </li>
      } @empty {
        @if (notes.hasValue()) {
          <li class="sub" i18n>Aucune note pour l'instant.</li>
        }
      }
    </ul>

    <form (ngSubmit)="add()" class="new">
      <mat-form-field class="field">
        <mat-label i18n>Nouvelle note</mat-label>
        <textarea matInput [formControl]="draft" rows="3" [maxlength]="max"></textarea>
        <mat-hint i18n
          >Visible de tous les recruteurs de l'entreprise, jamais du candidat.</mat-hint
        >
        <mat-hint align="end">{{ draft.value.length }} / {{ max }}</mat-hint>
      </mat-form-field>
      <div class="actions">
        <button mat-flat-button type="submit" [disabled]="draft.invalid || saving()" i18n>
          Ajouter
        </button>
      </div>
    </form>
  `,
  styles: `
    .notes {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0 0 16px;
      padding: 0;
      list-style: none;
    }
    .note {
      padding: 12px 16px;
      border-radius: 12px;
      background: var(--mat-sys-surface-container-low);
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
    }
    .content {
      margin: 6px 0 0;
      white-space: pre-line;
      overflow-wrap: anywhere;
    }
    .field {
      width: 100%;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
  `,
})
export class NotesPanel {
  private readonly http = inject(HttpClient);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  readonly applicationId = input.required<string>();
  /** Le nombre de notes et l'historique du dossier ont changé. */
  readonly changed = output<void>();

  protected readonly max = MAX_LENGTH;
  protected readonly notes = httpResource<ApplicationNote[]>(() => this.base());
  protected readonly list = computed(() => (this.notes.hasValue() ? this.notes.value() : []));
  protected readonly saving = signal(false);
  protected readonly editing = signal<string | null>(null);

  private readonly contentValidators = [
    Validators.required,
    Validators.pattern(/\S/),
    Validators.maxLength(MAX_LENGTH),
  ];
  protected readonly draft = new FormControl('', {
    nonNullable: true,
    validators: this.contentValidators,
  });
  protected readonly editor = new FormControl('', {
    nonNullable: true,
    validators: this.contentValidators,
  });

  protected add(): void {
    if (this.draft.invalid) {
      return;
    }
    this.saving.set(true);
    this.http.post<ApplicationNote>(this.base(), { content: this.draft.value.trim() }).subscribe({
      next: () => {
        this.saving.set(false);
        this.draft.reset();
        this.refresh();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.report(error);
      },
    });
  }

  protected startEdit(note: ApplicationNote): void {
    this.editor.setValue(note.content);
    this.editing.set(note.id);
  }

  protected saveEdit(note: ApplicationNote): void {
    this.http
      .put<ApplicationNote>(`${this.base()}/${note.id}`, { content: this.editor.value.trim() })
      .subscribe({
        next: () => {
          this.editing.set(null);
          this.notes.reload();
        },
        error: (error: unknown) => this.report(error),
      });
  }

  protected remove(note: ApplicationNote): void {
    confirm(this.dialog, {
      title: $localize`Supprimer cette note ?`,
      message: $localize`Elle disparaîtra pour toute l'équipe.`,
      confirmLabel: $localize`Supprimer`,
    }).subscribe((confirmed) => {
      if (confirmed) {
        this.http.delete<void>(`${this.base()}/${note.id}`).subscribe({
          next: () => this.refresh(),
          error: (error: unknown) => this.report(error),
        });
      }
    });
  }

  private base(): string {
    return `/api/v1/applications/${this.applicationId()}/notes`;
  }

  private refresh(): void {
    this.notes.reload();
    this.changed.emit();
  }

  private report(error: unknown): void {
    if (isApiError(error)) {
      this.notifications.error(error.detail ?? error.title);
    }
  }
}

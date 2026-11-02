import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/** Notifications globales en bas d'écran, pour les erreurs que l'écran courant ne gère pas lui-même. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  error(message: string): void {
    this.snackBar.open(message, $localize`Fermer`, {
      duration: 8000,
      panelClass: 'app-snackbar-error',
      politeness: 'assertive',
    });
  }

  success(message: string): void {
    this.snackBar.open(message, undefined, { duration: 4000, politeness: 'polite' });
  }
}

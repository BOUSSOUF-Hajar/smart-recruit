import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthStore } from '../../core/auth/auth.store';
import { homeFor } from '../../core/auth/home-route';

/**
 * Mise en page des pages publiques : en-tête, zone de contenu, pied de page.
 * Les espaces connectés ont leur propre `AppLayout` (barre latérale selon le rôle).
 */
@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatToolbarModule, MatButtonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main-content" i18n>Aller au contenu</a>

    <mat-toolbar class="header" role="banner">
      <a class="brand" routerLink="/">SmartRecruit</a>
      <nav class="nav" aria-label="Navigation principale" i18n-aria-label>
        <a mat-button routerLink="/jobs" routerLinkActive="active" ariaCurrentWhenActive="page" i18n
          >Offres</a
        >
        @if (store.isAuthenticated()) {
          <a mat-flat-button [routerLink]="home()" i18n>Mon espace</a>
        } @else {
          <a mat-button routerLink="/login" routerLinkActive="active" i18n>Connexion</a>
          <a mat-flat-button routerLink="/register" i18n>Créer un compte</a>
        }
      </nav>
    </mat-toolbar>

    <main id="main-content" class="content" tabindex="-1">
      <router-outlet />
    </main>

    <footer class="footer">
      <small i18n>SmartRecruit — projet de démonstration</small>
    </footer>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100%;
    }
    .header {
      gap: 16px;
      background: var(--mat-sys-surface-container);
    }
    .brand {
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-large);
      text-decoration: none;
    }
    .nav {
      display: flex;
      gap: 8px;
      margin-left: auto;
    }
    .active {
      background: var(--mat-sys-secondary-container);
    }
    .content {
      flex: 1;
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 16px;
      box-sizing: border-box;
      outline: none;
    }
    .footer {
      padding: 16px;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
    .skip-link {
      position: absolute;
      left: -9999px;
    }
    .skip-link:focus {
      left: 16px;
      top: 8px;
      z-index: 10;
      padding: 8px 12px;
      background: var(--mat-sys-surface);
    }
  `,
})
export class PublicLayout {
  protected readonly store = inject(AuthStore);
  protected readonly home = computed(() => homeFor(this.store.role()));
}

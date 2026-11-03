import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuthService } from '../../core/auth/auth.service';
import { AuthStore } from '../../core/auth/auth.store';
import { FRENCH_PAGINATOR } from '../../core/i18n/french-paginator-intl';
import { navigationFor } from './navigation';

/** Largeur sous laquelle la barre latérale devient un tiroir (architecture frontend §7). */
const COMPACT_LAYOUT = '(max-width: 959.98px)';

/**
 * Mise en page des espaces connectés : barre latérale selon le rôle, menu utilisateur, contenu.
 * Tant que le mot de passe temporaire n'est pas changé, la navigation est masquée (US-11).
 */
@Component({
  selector: 'app-app-layout',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  // Paginateurs en français dans toutes les listes des espaces connectés
  providers: [FRENCH_PAGINATOR],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main-content" i18n>Aller au contenu</a>

    <mat-toolbar class="header">
      @if (compact() && navigation().length) {
        <button
          mat-icon-button
          type="button"
          (click)="sidenav.toggle()"
          aria-label="Ouvrir le menu"
          i18n-aria-label
        >
          <mat-icon>menu</mat-icon>
        </button>
      }
      <a class="brand" routerLink="/">SmartRecruit</a>
      <span class="spacer"></span>
      <button mat-button type="button" [matMenuTriggerFor]="userMenu" class="user-button">
        <mat-icon>account_circle</mat-icon>
        <span class="user-name">{{ store.displayName() }}</span>
      </button>
      <mat-menu #userMenu="matMenu" xPosition="before">
        <p class="menu-email">{{ store.user()?.email }}</p>
        <mat-divider />
        <a mat-menu-item routerLink="/account/password">
          <mat-icon>key</mat-icon>
          <span i18n>Changer de mot de passe</span>
        </a>
        <button mat-menu-item type="button" (click)="logout()">
          <mat-icon>logout</mat-icon>
          <span i18n>Se déconnecter</span>
        </button>
      </mat-menu>
    </mat-toolbar>

    <mat-sidenav-container class="container">
      <mat-sidenav
        #sidenav
        [mode]="compact() ? 'over' : 'side'"
        [opened]="!compact() && navigation().length > 0"
        class="sidenav"
      >
        <nav aria-label="Navigation de l'espace" i18n-aria-label>
          <mat-nav-list>
            @for (item of navigation(); track item.link) {
              <a
                mat-list-item
                [routerLink]="item.link"
                routerLinkActive
                #active="routerLinkActive"
                [activated]="active.isActive"
                [routerLinkActiveOptions]="{ exact: true }"
                ariaCurrentWhenActive="page"
                (click)="compact() && sidenav.close()"
              >
                <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
                <span matListItemTitle>{{ item.label }}</span>
              </a>
            }
          </mat-nav-list>
        </nav>
      </mat-sidenav>

      <mat-sidenav-content>
        <main id="main-content" class="content" tabindex="-1">
          <router-outlet />
        </main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100%;
    }
    .header {
      gap: 8px;
      background: var(--mat-sys-surface-container);
    }
    .brand {
      color: var(--mat-sys-primary);
      font: var(--mat-sys-title-large);
      text-decoration: none;
    }
    .spacer {
      flex: 1;
    }
    .menu-email {
      margin: 8px 16px;
      color: var(--mat-sys-on-surface-variant);
      font: var(--mat-sys-body-small);
    }
    .container {
      flex: 1;
    }
    .sidenav {
      width: 240px;
    }
    .content {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px 16px;
      outline: none;
    }
    @media (max-width: 599.98px) {
      .user-name {
        display: none;
      }
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
export class AppLayout {
  protected readonly store = inject(AuthStore);
  private readonly auth = inject(AuthService);

  protected readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe(COMPACT_LAYOUT)
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly navigation = computed(() =>
    this.store.mustChangePassword() ? [] : navigationFor(this.store.role()),
  );

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}

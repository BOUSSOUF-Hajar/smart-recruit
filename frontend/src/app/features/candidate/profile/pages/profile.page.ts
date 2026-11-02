import { ChangeDetectionStrategy, Component, inject, input, linkedSignal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { BasicInfoForm } from '../components/basic-info-form';
import { CareerSection } from '../components/career-section';
import { CompletenessCard } from '../components/completeness-card';
import { LanguagesEditor } from '../components/languages-editor';
import { LinksEditor } from '../components/links-editor';
import { ResumePanel } from '../components/resume-panel';
import { SkillsEditor } from '../components/skills-editor';
import { ProfileStore } from '../data-access/profile.store';

/**
 * Profil candidat à onglets (US-13 à US-18, US-20). Chaque onglet enregistre sa partie
 * indépendamment ; la complétude se met à jour après chaque enregistrement.
 */
@Component({
  selector: 'app-profile-page',
  imports: [
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTabsModule,
    BasicInfoForm,
    CareerSection,
    CompletenessCard,
    LanguagesEditor,
    LinksEditor,
    ResumePanel,
    SkillsEditor,
  ],
  providers: [ProfileStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n>Mon profil</h1>

    @if (store.profile(); as profile) {
      <app-completeness-card
        [completeness]="profile.completeness"
        (goTo)="selectedTab.set($event)"
      />

      <mat-tab-group
        class="tabs"
        [selectedIndex]="selectedTab()"
        (selectedIndexChange)="selectedTab.set($event)"
        mat-stretch-tabs="false"
        dynamicHeight
      >
        <mat-tab label="Informations" i18n-label>
          <div class="tab"><app-basic-info-form [profile]="profile" /></div>
        </mat-tab>
        <mat-tab label="Parcours" i18n-label>
          <div class="tab">
            <app-career-section
              [experiences]="profile.experiences"
              [educations]="profile.educations"
              (changed)="store.reload()"
            />
          </div>
        </mat-tab>
        <mat-tab label="Compétences et langues" i18n-label>
          <div class="tab">
            <app-skills-editor [skills]="profile.skills" />
            <div class="spacer"></div>
            <app-languages-editor [languages]="profile.languages" />
          </div>
        </mat-tab>
        <mat-tab label="Liens" i18n-label>
          <div class="tab"><app-links-editor [links]="profile.links" /></div>
        </mat-tab>
        <mat-tab label="CV" i18n-label>
          <div class="tab">
            <app-resume-panel [resume]="profile.resume" (changed)="store.reload()" />
          </div>
        </mat-tab>
      </mat-tab-group>
    } @else if (store.resource.error()) {
      <div class="state" role="alert">
        <p i18n>Le profil n'a pas pu être chargé.</p>
        <button mat-flat-button type="button" (click)="store.reload()" i18n>Réessayer</button>
      </div>
    } @else {
      <div class="state" aria-busy="true">
        <mat-spinner diameter="40" />
      </div>
    }
  `,
  styles: `
    h1 {
      margin: 0 0 16px;
      font: var(--mat-sys-headline-small);
    }
    .tabs {
      margin-top: 16px;
    }
    .tab {
      padding: 24px 4px;
    }
    .spacer {
      height: 32px;
    }
    .state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 48px 0;
    }
  `,
})
export class ProfilePage {
  protected readonly store = inject(ProfileStore);
  /** Paramètre de requête `tab` : un lien « compléter le titre » ouvre directement le bon onglet. */
  readonly tab = input<string>();
  protected readonly selectedTab = linkedSignal(() => {
    const index = Number(this.tab());
    return Number.isInteger(index) && index >= 0 && index <= 4 ? index : 0;
  });
}

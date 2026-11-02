import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ProfilePage } from './profile.page';
import { aProfile } from '../data-access/profile.testing';
import { SKILL_SEARCH } from '../../../../shared/ui/skill-autocomplete/skill-search';

describe('ProfilePage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SKILL_SEARCH, useValue: { search: () => of([]), create: () => of() } },
      ],
    });
  });

  it('affiche la complétude et les cinq onglets une fois le profil chargé', async () => {
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/candidates/me/profile')
      .flush(aProfile({ completeness: { percent: 35, missing: ['experience'] } }));
    await fixture.whenStable();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Profil complété à 35 %');
    const tabs = Array.from(element.querySelectorAll('[role="tab"]')).map((tab) =>
      tab.textContent?.trim(),
    );
    expect(tabs).toEqual(['Informations', 'Parcours', 'Compétences et langues', 'Liens', 'CV']);
    // US-13 : aucun champ discriminant dans le formulaire d'informations
    expect(element.textContent).not.toMatch(/âge|sexe|nationalité|situation familiale/i);
  });

  it('propose de réessayer si le chargement échoue', async () => {
    const fixture = TestBed.createComponent(ProfilePage);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/candidates/me/profile')
      .flush({ code: 'INTERNAL_ERROR' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain("Le profil n'a pas pu être chargé.");
  });
});

import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from './app.routes';

describe('Routage de premier niveau', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    harness = await RouterTestingHarness.create();
  });

  it("affiche la mise en page publique, avec l'en-tête et la zone de contenu", async () => {
    await harness.navigateByUrl('/jobs');

    const page: HTMLElement = harness.fixture.nativeElement;
    expect(page.querySelector('mat-toolbar')?.textContent).toContain('SmartRecruit');
    expect(page.querySelector('main#main-content')?.textContent).toContain("Offres d'emploi");
  });

  it('redirige la racine vers la liste des offres', async () => {
    await harness.navigateByUrl('/');

    expect(TestBed.inject(Router).url).toBe('/jobs');
  });

  it('envoie un visiteur qui ouvre un espace protégé vers la connexion', async () => {
    await harness.navigateByUrl('/candidate');

    expect(TestBed.inject(Router).url).toBe('/login?returnUrl=%2Fcandidate');
    expect(harness.fixture.nativeElement.textContent).toContain('Connexion');
  });

  it('affiche la page « introuvable » pour une route inconnue', async () => {
    await harness.navigateByUrl('/cette-page-n-existe-pas');

    expect(TestBed.inject(Router).url).toBe('/not-found');
    expect(harness.fixture.nativeElement.textContent).toContain('Page introuvable');
  });
});

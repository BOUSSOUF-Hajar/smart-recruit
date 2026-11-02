import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { ProfileStore } from './profile.store';
import { aProfile } from './profile.testing';

describe('ProfileStore', () => {
  let store: ProfileStore;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ProfileStore],
    });
    store = TestBed.inject(ProfileStore);
    backend = TestBed.inject(HttpTestingController);
    // httpResource part au premier cycle de détection ; l'application reste « instable » tant
    // que la requête est en cours, d'où tick() avant de répondre, et whenStable() après
    TestBed.tick();
    backend.expectOne('/api/v1/candidates/me/profile').flush(aProfile());
    await TestBed.inject(ApplicationRef).whenStable();
  });

  afterEach(() => backend.verify());

  it('charge le profil du candidat connecté', () => {
    expect(store.profile()?.firstName).toBe('Salma');
    expect(store.completeness()?.percent).toBe(10);
  });

  it('envoie la version lue et affiche le profil renvoyé, sans recharger', async () => {
    const saved = firstValueFrom(
      store.saveBasics({
        firstName: 'Salma',
        lastName: 'Idrissi',
        phone: null,
        location: null,
        headline: 'Développeuse Java',
        summary: null,
        yearsOfExperience: 4,
      }),
    );
    const request = backend.expectOne('/api/v1/candidates/me/profile');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.version).toBe(3);
    request.flush(aProfile({ headline: 'Développeuse Java', version: 4 }));
    await saved;

    expect(store.profile()?.headline).toBe('Développeuse Java');
    expect(store.profile()?.version).toBe(4);
  });

  it('remplace les compétences dans un objet enveloppe', async () => {
    const saved = firstValueFrom(
      store.saveSkills([{ skillId: 's1', level: 'ADVANCED', years: 2 }]),
    );
    const request = backend.expectOne('/api/v1/candidates/me/skills');
    expect(request.request.body).toEqual({
      skills: [{ skillId: 's1', level: 'ADVANCED', years: 2 }],
    });
    request.flush(aProfile());
    await saved;
  });
});

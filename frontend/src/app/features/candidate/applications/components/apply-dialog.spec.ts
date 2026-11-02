import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ApiError } from '../../../../core/errors/api-error';
import { CandidateApplicationsApi } from '../data-access/candidate-applications.api';
import { CandidateApplication } from '../data-access/candidate-applications.models';
import { ApplyDialog } from './apply-dialog';

describe('ApplyDialog', () => {
  const apply = vi.fn<(jobId: string) => Observable<CandidateApplication>>();
  const close = vi.fn();

  beforeEach(() => {
    apply.mockReset();
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CandidateApplicationsApi, useValue: { apply } },
        { provide: MatDialogRef, useValue: { close } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { jobId: 'j-1', jobTitle: 'Développeur Java', companyName: 'Atlas' },
        },
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(ApplyDialog);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/candidates/me/profile')
      .flush({
        firstName: 'Salma',
        lastName: 'Idrissi',
        headline: 'Développeuse Java',
        skills: [{}],
        resume: { originalFilename: 'cv-salma.pdf' },
      });
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const confirmButton = () =>
      [...element.querySelectorAll('button')].find((b) => b.textContent?.includes('Confirmer'))!;
    return { fixture, element, confirmButton };
  }

  it('montre ce qui sera transmis puis ferme avec la candidature créée', async () => {
    apply.mockReturnValue(of({ id: 'a-1' } as CandidateApplication));
    const { element, confirmButton } = await render();

    expect(element.textContent).toContain('CV : cv-salma.pdf');
    confirmButton().click();

    expect(apply).toHaveBeenCalledWith('j-1');
    expect(close).toHaveBeenCalledWith({ applicationId: 'a-1', created: true });
  });

  it('liste les manques du profil avec un lien vers le bon onglet', async () => {
    const error: ApiError = {
      status: 422,
      code: 'PROFILE_INCOMPLETE',
      title: 'Profil incomplet',
      errors: [],
      extensions: { missing: ['headline', 'resume'] },
    };
    apply.mockReturnValue(throwError(() => error));
    const { fixture, element, confirmButton } = await render();

    confirmButton().click();
    await fixture.whenStable();

    const links = [...element.querySelectorAll('[role=alert] a')];
    expect(links.map((a) => a.textContent?.trim())).toEqual(['Titre professionnel', 'Un CV']);
    expect(links[1].getAttribute('href')).toBe('/candidate/profile?tab=4');
    expect(close).not.toHaveBeenCalled();
  });

  it('mène à la candidature existante si le candidat avait déjà postulé', async () => {
    const error: ApiError = {
      status: 409,
      code: 'APPLICATION_ALREADY_EXISTS',
      title: 'Conflit',
      errors: [],
      extensions: { applicationId: 'a-0' },
    };
    apply.mockReturnValue(throwError(() => error));
    const { confirmButton } = await render();

    confirmButton().click();

    expect(close).toHaveBeenCalledWith({ applicationId: 'a-0', created: false });
  });
});

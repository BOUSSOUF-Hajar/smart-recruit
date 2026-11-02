import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Observable, of } from 'rxjs';
import { AuthStore } from '../../../../core/auth/auth.store';
import { InterviewsApi } from '../data-access/interviews.api';
import { InterviewRequest, RecruiterInterview } from '../data-access/interviews.models';
import { InterviewDialog } from './interview-dialog';

describe('InterviewDialog', () => {
  const schedule =
    vi.fn<(applicationId: string, request: InterviewRequest) => Observable<RecruiterInterview>>();
  const close = vi.fn();

  beforeEach(() => {
    schedule.mockReset().mockReturnValue(of({ id: 'i-1' } as RecruiterInterview));
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InterviewsApi, useValue: { schedule } },
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { applicationId: 'a-1' } },
        { provide: AuthStore, useValue: { user: () => ({ id: 'r-1' }) } },
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(InterviewDialog);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/v1/company/recruiters')
      .flush([{ id: 'r-1', name: 'Youssef Alami', jobTitle: null }]);
    await fixture.whenStable();
    return {
      fixture,
      element: fixture.nativeElement as HTMLElement,
      form: fixture.componentInstance['form'],
    };
  }

  it('exige un lien https pour une visioconférence, une adresse pour un entretien sur place', async () => {
    const { fixture, form } = await render();
    form.patchValue({ startsAt: '2099-11-02T10:00', type: 'VIDEO', meetingUrl: '' });
    expect(form.controls.meetingUrl.hasError('required')).toBe(true);
    form.controls.meetingUrl.setValue('http://meet.example.com');
    expect(form.controls.meetingUrl.valid).toBe(false);

    form.controls.type.setValue('ON_SITE');
    await fixture.whenStable();
    expect(form.controls.meetingUrl.valid).toBe(true);
    expect(form.controls.address.hasError('required')).toBe(true);

    form.controls.type.setValue('PHONE');
    expect(form.valid).toBe(true);
  });

  it('refuse une date passée', async () => {
    const { form } = await render();
    form.controls.startsAt.setValue('2020-01-01T10:00');
    expect(form.controls.startsAt.hasError('past')).toBe(true);
  });

  it("envoie l'heure en UTC, sans le champ propre à un autre type", async () => {
    const { element, form } = await render();
    form.patchValue({
      startsAt: '2099-11-02T10:00',
      type: 'VIDEO',
      meetingUrl: 'https://meet.example.com/abc',
      address: 'adresse oubliée',
    });

    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    const [applicationId, request] = schedule.mock.calls[0];
    expect(applicationId).toBe('a-1');
    expect(request.startsAt).toBe(new Date('2099-11-02T10:00').toISOString());
    expect(request.address).toBeNull();
    expect(request.participantIds).toEqual(['r-1']); // l'auteur participe par défaut
    expect(close).toHaveBeenCalledWith({ id: 'i-1' });
  });
});

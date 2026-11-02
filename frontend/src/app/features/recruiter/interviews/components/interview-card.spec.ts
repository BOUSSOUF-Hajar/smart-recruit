import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RecruiterInterview } from '../data-access/interviews.models';
import { InterviewCard } from './interview-card';

describe('InterviewCard', () => {
  function render(partial: Partial<RecruiterInterview>) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(InterviewCard);
    fixture.componentRef.setInput('interview', {
      id: 'i-1',
      application: {
        id: 'a-1',
        candidateName: 'Salma Idrissi',
        jobOfferId: 'j-1',
        jobTitle: 'Dev',
      },
      startsAt: '2099-01-01T10:00:00Z',
      durationMinutes: 45,
      type: 'VIDEO',
      meetingUrl: 'https://meet.example.com/abc',
      address: null,
      candidateInstructions: null,
      status: 'SCHEDULED',
      cancellationReason: null,
      feedback: null,
      participants: [{ id: 'r-1', name: 'Youssef Alami' }],
      scheduledBy: { id: 'r-1', name: 'Youssef Alami' },
      version: 0,
      ...partial,
    } satisfies RecruiterInterview);
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    return [...element.querySelectorAll('button')].map((b) => b.textContent?.trim());
  }

  it("ne propose la clôture qu'une fois l'entretien commencé", () => {
    expect(render({})).toEqual(['Modifier', 'Annuler']);
  });

  it('propose la clôture après le début', () => {
    expect(render({ startsAt: '2020-01-01T10:00:00Z' })).toEqual([
      'Modifier',
      'Annuler',
      'Clôturer',
    ]);
  });

  it("n'offre plus aucune action sur un entretien terminé ou annulé", () => {
    expect(render({ status: 'CANCELLED' })).toEqual([]);
  });
});

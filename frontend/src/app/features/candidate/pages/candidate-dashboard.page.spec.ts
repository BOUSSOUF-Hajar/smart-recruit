import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CandidateDashboard, CandidateDashboardPage } from './candidate-dashboard.page';

describe('CandidateDashboardPage', () => {
  const dashboard: CandidateDashboard = {
    applicationsByStatus: { IN_REVIEW: 2, SENT: 1, NOT_SELECTED: 1 },
    upcomingInterviews: [
      {
        id: 'i-1',
        startsAt: '2026-10-23T09:00:00Z',
        durationMinutes: 45,
        type: 'VIDEO',
        jobTitle: 'Développeur Java',
        companyName: 'Atlas Digital',
      },
    ],
    resume: { present: true, uploadedAt: '2026-10-01T10:00:00Z' },
    profileCompleteness: { percent: 80, missing: ['SKILLS'] },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function render(body: CandidateDashboard) {
    const fixture = TestBed.createComponent(CandidateDashboardPage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/candidates/me/dashboard').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('montre les libellés candidat dans l’ordre du processus, les entretiens et la complétude', async () => {
    const element = await render(dashboard);
    const labels = Array.from(element.querySelectorAll('section.card:first-of-type li span')).map(
      (s) => s.textContent!.trim(),
    );

    expect(labels.length).toBe(3);
    expect(labels[0]).not.toBe(labels[1]);
    expect(element.textContent).toContain('Atlas Digital');
    expect(element.textContent).toContain('80 %');
    expect(element.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('80');
    expect(element.querySelector('.cta')).toBeNull();
  });

  it('invite à téléverser le CV quand il manque', async () => {
    const element = await render({ ...dashboard, resume: { present: false, uploadedAt: null } });
    const cta = element.querySelector('.cta a');

    expect(cta).not.toBeNull();
    expect(decodeURIComponent(cta!.getAttribute('href')!)).toBe('/candidate/profile?tab=4');
  });
});

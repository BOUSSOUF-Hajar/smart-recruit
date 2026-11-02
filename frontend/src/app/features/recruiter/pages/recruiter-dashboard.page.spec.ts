import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RecruiterDashboardPage } from './recruiter-dashboard.page';
import { RecruiterDashboard, daysAgo } from './recruiter-dashboard.models';

describe('RecruiterDashboardPage', () => {
  const dashboard: RecruiterDashboard = {
    jobsByStatus: { PUBLISHED: 3, DRAFT: 1 },
    applicationsReceived: { last7Days: 12, last30Days: 40 },
    inScreening: 5,
    shortlisted: 2,
    upcomingInterviews: [
      {
        id: 'i-1',
        applicationId: 'a-1',
        startsAt: '2026-10-23T09:00:00Z',
        type: 'VIDEO',
        candidateName: 'Salma Idrissi',
        jobTitle: 'Développeur Java',
      },
    ],
    hires: { last30Days: 1 },
    perJob: [
      {
        jobId: 'j-1',
        title: 'Développeur Java',
        status: 'PUBLISHED',
        countsByStatus: { SUBMITTED: 4, SCREENING: 2 },
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function render(respond: (http: HttpTestingController) => void) {
    const fixture = TestBed.createComponent(RecruiterDashboardPage);
    fixture.detectChanges();
    TestBed.tick();
    respond(TestBed.inject(HttpTestingController));
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('relie chaque indicateur à la liste filtrée correspondante', async () => {
    const element = await render((http) =>
      http.expectOne('/api/v1/company/dashboard').flush(dashboard),
    );
    const hrefs = Array.from(element.querySelectorAll('a')).map((a) =>
      decodeURIComponent(a.getAttribute('href')!),
    );

    expect(element.textContent).toContain('12');
    expect(element.textContent).toContain('40 sur 30 jours');
    expect(hrefs).toContain(`/recruiter/applications?submittedFrom=${daysAgo(7)}`);
    expect(hrefs).toContain('/recruiter/applications?status=SCREENING');
    expect(hrefs).toContain('/recruiter/applications?status=SHORTLISTED');
    expect(hrefs).toContain('/recruiter/applications?status=HIRED');
    expect(hrefs).toContain('/recruiter/jobs?status=PUBLISHED');
    expect(hrefs).toContain('/recruiter/applications/a-1');
    expect(hrefs).toContain('/recruiter/applications?jobOfferId=j-1&status=SUBMITTED');
  });

  it('affiche 0 sans lien pour un statut vide et un état vide sans entretien', async () => {
    const element = await render((http) =>
      http.expectOne('/api/v1/company/dashboard').flush({ ...dashboard, upcomingInterviews: [] }),
    );
    const hrefs = Array.from(element.querySelectorAll('a')).map((a) =>
      decodeURIComponent(a.getAttribute('href')!),
    );

    expect(hrefs).not.toContain('/recruiter/applications?jobOfferId=j-1&status=HIRED');
    expect(element.textContent).toContain('Aucun entretien prévu.');
  });

  it('propose de réessayer quand le chargement échoue', async () => {
    const element = await render((http) =>
      http
        .expectOne('/api/v1/company/dashboard')
        .flush(null, { status: 500, statusText: 'Erreur' }),
    );
    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      "n'a pas pu être chargé",
    );
  });
});

describe('daysAgo', () => {
  it('compte en jours calendaires locaux, changement de mois compris', () => {
    expect(daysAgo(6, new Date(2026, 9, 3, 23, 30))).toBe('2026-09-27');
  });
});

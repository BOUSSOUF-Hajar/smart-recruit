import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AdminDashboard } from '../data-access/admin.api';
import { AdminDashboardPage } from './admin-dashboard.page';

describe('AdminDashboardPage', () => {
  const dashboard: AdminDashboard = {
    usersByRole: {
      CANDIDATE: { active: 120, disabled: 3 },
      RECRUITER: { active: 14, disabled: 2 },
      ADMIN: { active: 2, disabled: 0 },
    },
    companies: { active: 6, disabled: 1 },
    jobsByStatus: { PUBLISHED: 9, DRAFT: 2, CLOSED: 4, ARCHIVED: 1 },
    applications: { total: 310, last30Days: 42 },
    ai: { status: 'AVAILABLE', model: 'qwen3.5:9b' },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  async function render(body: AdminDashboard) {
    const fixture = TestBed.createComponent(AdminDashboardPage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/admin/dashboard').flush(body);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('relie les comptes et les entreprises à leurs listes filtrées', async () => {
    const element = await render(dashboard);
    const hrefs = Array.from(element.querySelectorAll('a')).map((a) =>
      decodeURIComponent(a.getAttribute('href')!),
    );

    expect(hrefs).toContain('/admin/users?role=RECRUITER&enabled=true');
    expect(hrefs).toContain('/admin/companies?active=true');
    expect(element.textContent).toContain('2 désactivé(s)');
    expect(element.textContent).toContain('310');
    expect(element.textContent).toContain('42 sur 30 jours');
    expect(element.textContent).toContain('Disponible');
  });

  it('signale une IA indisponible sans bloquer le reste du tableau de bord', async () => {
    const element = await render({
      ...dashboard,
      ai: { status: 'UNAVAILABLE', model: 'qwen3.5:9b' },
    });

    expect(element.querySelector('.ai.down')?.textContent).toContain('Indisponible');
    expect(element.textContent).toContain('le reste de la plateforme fonctionne');
    expect(element.textContent).toContain('120');
  });
});

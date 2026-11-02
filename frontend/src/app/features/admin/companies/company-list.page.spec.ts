import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { NotificationService } from '../../../core/errors/notification.service';
import { AdminApi, Company } from '../data-access/admin.api';
import { CompanyListPage, deactivationMessage } from './company-list.page';

describe('CompanyListPage', () => {
  const company = {
    id: 'c-1',
    name: 'Atlas Digital',
    active: true,
    recruiterCount: 2,
    jobCount: 5,
    publishedJobCount: 3,
    version: 1,
  } as Company;
  const deactivateCompany = vi.fn();
  const success = vi.fn();
  const open = vi.fn();

  beforeEach(() => {
    deactivateCompany
      .mockReset()
      .mockReturnValue(
        of({ company: { ...company, active: false }, closedJobs: 3, disabledRecruiters: 2 }),
      );
    success.mockReset();
    open.mockReset().mockReturnValue({ afterClosed: () => of(true) });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AdminApi, useValue: { deactivateCompany } },
        { provide: MatDialog, useValue: { open } },
        { provide: NotificationService, useValue: { success, error: vi.fn() } },
      ],
    });
  });

  it('annonce la cascade avant de désactiver, puis ce qu’elle a fait', async () => {
    expect(deactivationMessage(company)).toContain('3 offre(s) publiée(s)');
    expect(deactivationMessage(company)).toContain('2 recruteur(s)');

    const fixture = TestBed.createComponent(CompanyListPage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne((req) => req.url === '/api/v1/admin/companies')
      .flush({ content: [company], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    await fixture.whenStable();

    fixture.componentInstance['deactivate'](company);

    expect(open.mock.calls[0][1].data.message).toBe(deactivationMessage(company));
    expect(deactivateCompany).toHaveBeenCalledWith('c-1');
    expect(success).toHaveBeenCalledWith(
      expect.stringContaining('3 offre(s) clôturée(s), 2 recruteur(s)'),
    );
  });
});

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminApi, AdminUser } from '../data-access/admin.api';
import { UserListPage, userParams } from './user-list.page';

describe('UserListPage', () => {
  const setUserEnabled = vi.fn();
  const open = vi.fn();
  const user: AdminUser = {
    id: 'u-1',
    email: 'rh@atlas.ma',
    firstName: 'Youssef',
    lastName: 'Alami',
    role: 'RECRUITER',
    enabled: true,
    mustChangePassword: false,
    lastLoginAt: null,
    createdAt: '2026-10-01T10:00:00Z',
    companyId: 'c-1',
    companyName: 'Atlas Digital',
    jobTitle: 'RH',
    version: 3,
  };

  beforeEach(() => {
    setUserEnabled.mockReset().mockReturnValue(of({ ...user, enabled: false }));
    open.mockReset().mockReturnValue({ afterClosed: () => of(true) });
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AdminApi, useValue: { setUserEnabled } },
        { provide: MatDialog, useValue: { open } },
      ],
    });
  });

  it("n'envoie que les filtres renseignés", () => {
    expect(userParams({ role: 'ADMIN', q: '', enabled: 'false' })).toEqual({
      size: '20',
      role: 'ADMIN',
      enabled: 'false',
    });
  });

  it('applique les filtres de l’URL et désactive un compte après confirmation', async () => {
    const fixture = TestBed.createComponent(UserListPage);
    fixture.componentRef.setInput('role', 'RECRUITER');
    fixture.componentRef.setInput('enabled', 'true');
    fixture.detectChanges();
    TestBed.tick();
    const http = TestBed.inject(HttpTestingController);
    const request = http.expectOne((req) => req.url === '/api/v1/admin/users');
    expect(request.request.params.get('role')).toBe('RECRUITER');
    expect(request.request.params.get('enabled')).toBe('true');
    request.flush({ content: [user], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    http
      .expectOne((req) => req.url === '/api/v1/admin/companies')
      .flush({
        content: [],
        page: 0,
        size: 100,
        totalElements: 0,
        totalPages: 0,
      });
    await fixture.whenStable();
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Atlas Digital');

    fixture.componentInstance['setEnabled'](user, false);

    expect(open).toHaveBeenCalled();
    expect(setUserEnabled).toHaveBeenCalledWith('u-1', false);
  });
});

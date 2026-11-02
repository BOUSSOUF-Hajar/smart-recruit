import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { ApiError } from '../../../core/errors/api-error';
import { AdminApi, CreatedStaff, CreateStaffRequest } from '../data-access/admin.api';
import { CreateUserPage } from './create-user.page';

describe('CreateUserPage', () => {
  const createStaff = vi.fn<(request: CreateStaffRequest) => Observable<CreatedStaff>>();
  const open = vi.fn();
  const created = { user: { id: 'u-1' }, temporaryPassword: 'K7PX-M2QA-9TZR-WD4H' } as CreatedStaff;

  beforeEach(() => {
    createStaff.mockReset();
    open.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AdminApi, useValue: { createStaff } },
        { provide: MatDialog, useValue: { open } },
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(CreateUserPage);
    fixture.detectChanges();
    TestBed.tick();
    TestBed.inject(HttpTestingController)
      .expectOne(
        (request) =>
          request.url === '/api/v1/admin/companies' && request.params.get('active') === 'true',
      )
      .flush({
        content: [{ id: 'c-1', name: 'Atlas Digital', active: true }],
        page: 0,
        size: 100,
        totalElements: 1,
        totalPages: 1,
      });
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const form = fixture.componentInstance['form'];
    return { fixture, element, form };
  }

  const identity = { email: 'rh@atlas.ma', firstName: 'Youssef', lastName: 'Alami' };

  it('exige une entreprise pour un recruteur et affiche le mot de passe temporaire après la création', async () => {
    createStaff.mockReturnValue(of(created));
    const { fixture, element, form } = await render();
    form.patchValue(identity);

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(createStaff).not.toHaveBeenCalled();

    form.controls.companyId.setValue('c-1');
    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(createStaff).toHaveBeenCalledWith(
      expect.objectContaining({ role: 'RECRUITER', companyId: 'c-1' }),
    );
    expect(open).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: created, disableClose: true }),
    );
    expect(form.controls.email.value).toBe('');
  });

  it("n'envoie pas d'entreprise pour un administrateur", async () => {
    createStaff.mockReturnValue(of(created));
    const { fixture, element, form } = await render();
    form.patchValue({ ...identity, companyId: 'c-1' });
    form.controls.role.setValue('ADMIN');
    await fixture.whenStable();

    expect(element.querySelector('mat-select')).toBeNull();
    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(createStaff).toHaveBeenCalledWith(
      expect.objectContaining({ role: 'ADMIN', companyId: null }),
    );
  });

  it('signale un email déjà utilisé sous le champ', async () => {
    const error: ApiError = {
      status: 409,
      code: 'EMAIL_ALREADY_USED',
      title: 'Conflit',
      errors: [],
      extensions: {},
    };
    createStaff.mockReturnValue(throwError(() => error));
    const { element, form } = await render();
    form.patchValue({ ...identity, companyId: 'c-1' });

    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(form.controls.email.errors).toEqual({ server: 'Un compte existe déjà avec cet email.' });
    expect(open).not.toHaveBeenCalled();
  });
});

import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { AdminApi, AdminUser, Company } from '../data-access/admin.api';
import { UserDialog, UserDialogData } from './user-dialog';

describe('UserDialog', () => {
  const updateUser = vi.fn();
  const close = vi.fn();
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
  const companies = [
    { id: 'c-1', name: 'Atlas Digital', active: true },
    { id: 'c-2', name: 'Nova Conseil', active: true },
    { id: 'c-3', name: 'Ancienne', active: false },
  ] as Company[];

  function create(data: UserDialogData) {
    updateUser.mockReset().mockReturnValue(of(user));
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: AdminApi, useValue: { updateUser } },
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: data },
      ],
    });
    const fixture = TestBed.createComponent(UserDialog);
    fixture.detectChanges();
    return fixture;
  }

  it("prévient que changer d'entreprise ferme les sessions et envoie la version lue", () => {
    const fixture = create({ user, companies });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('ferme les sessions');

    fixture.componentInstance['form'].controls.companyId.setValue('c-2');
    fixture.detectChanges();
    expect(element.textContent).toContain('ferme les sessions');
    // Les entreprises désactivées ne sont pas proposées
    expect(fixture.componentInstance['selectableCompanies'].map((c) => c.id)).toEqual([
      'c-1',
      'c-2',
    ]);

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(updateUser).toHaveBeenCalledWith('u-1', {
      firstName: 'Youssef',
      lastName: 'Alami',
      companyId: 'c-2',
      jobTitle: 'RH',
      version: 3,
    });
    expect(close).toHaveBeenCalledWith(user);
  });

  it("n'envoie ni entreprise ni poste pour un administrateur", () => {
    const fixture = create({
      user: { ...user, role: 'ADMIN', companyId: null, companyName: null },
      companies,
    });
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('mat-select')).toBeNull();

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(updateUser).toHaveBeenCalledWith(
      'u-1',
      expect.objectContaining({ companyId: null, jobTitle: null }),
    );
  });
});

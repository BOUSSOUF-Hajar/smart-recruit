import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TemporaryPasswordDialog } from './temporary-password-dialog';
import { CreatedStaff } from '../data-access/admin.api';

describe('TemporaryPasswordDialog', () => {
  const created: CreatedStaff = {
    user: {
      id: 'u-1',
      email: 'rh@entreprise.ma',
      firstName: 'Youssef',
      lastName: 'Alami',
      role: 'RECRUITER',
      companyId: 'c-1',
      enabled: true,
      mustChangePassword: true,
    },
    temporaryPassword: 'K7PX-M2QA-9TZR-WD4H',
  };
  const copy = vi.fn(() => true);

  beforeEach(() => {
    copy.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: created },
        { provide: Clipboard, useValue: { copy } },
      ],
    });
  });

  it('affiche le mot de passe temporaire une seule fois et permet de le copier', async () => {
    const fixture = TestBed.createComponent(TemporaryPasswordDialog);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('code')?.textContent).toBe('K7PX-M2QA-9TZR-WD4H');
    expect(element.textContent).toContain('rh@entreprise.ma');
    expect(element.textContent).toContain('ne sera plus jamais affiché');

    element.querySelector<HTMLButtonElement>('button[aria-label]')!.click();
    await fixture.whenStable();

    expect(copy).toHaveBeenCalledWith('K7PX-M2QA-9TZR-WD4H');
    expect(element.querySelector('mat-icon')?.textContent).toBe('check');
  });
});

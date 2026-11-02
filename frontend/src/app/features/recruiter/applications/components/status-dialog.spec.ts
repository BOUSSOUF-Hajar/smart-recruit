import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Dossier } from '../data-access/recruiter-applications.models';
import { StatusDialog } from './status-dialog';

describe('StatusDialog', () => {
  const close = vi.fn();

  function render(partial: Partial<Dossier>) {
    close.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { status: 'SUBMITTED', version: 3, ...partial } },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    const fixture = TestBed.createComponent(StatusDialog);
    fixture.detectChanges();
    return {
      fixture,
      element: fixture.nativeElement as HTMLElement,
      form: fixture.componentInstance['form'],
    };
  }

  it('ne propose que les transitions envoyées par le serveur', () => {
    const { element } = render({ allowedTransitions: ['SCREENING', 'REJECTED'] });

    const labels = [...element.querySelectorAll('mat-radio-button')].map((b) =>
      b.textContent?.trim(),
    );
    expect(labels).toEqual(['Passer en présélection', 'Rejeter']);
  });

  it('exige un motif pour rejeter et renvoie la version lue', async () => {
    const { fixture, element, form } = render({ allowedTransitions: ['SCREENING', 'REJECTED'] });
    form.controls.targetStatus.setValue('REJECTED');
    await fixture.whenStable();

    element.querySelector('form')!.dispatchEvent(new Event('submit'));
    expect(close).not.toHaveBeenCalled();
    expect(element.textContent).toContain('définitif');

    form.patchValue({ rejectionReason: 'POSITION_FILLED', comment: '  Poste pourvu en interne ' });
    element.querySelector('form')!.dispatchEvent(new Event('submit'));

    expect(close).toHaveBeenCalledWith({
      targetStatus: 'REJECTED',
      rejectionReason: 'POSITION_FILLED',
      comment: 'Poste pourvu en interne',
      version: 3,
    });
  });

  it("explique pourquoi la proposition n'est pas encore possible", () => {
    const { element } = render({ status: 'INTERVIEW', allowedTransitions: ['REJECTED'] });

    expect(element.textContent).toContain('après au moins un entretien terminé');
  });
});

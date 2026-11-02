import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { applyServerErrors, toFormPath } from './apply-server-errors';
import { fieldErrorMessage } from './field-error';
import {
  chronologicalOrder,
  e164Phone,
  httpsUrl,
  matchingFields,
  notInFuture,
  uniqueBy,
} from './validators';

describe('applyServerErrors', () => {
  const form = () => new FormGroup({ email: new FormControl(''), password: new FormControl('') });

  it("place l'erreur du serveur sous le bon champ", () => {
    const group = form();

    const applied = applyServerErrors(group, [
      { field: 'email', message: 'doit être une adresse valide' },
    ]);

    expect(applied).toBe(true);
    expect(group.controls.email.errors).toEqual({ server: 'doit être une adresse valide' });
    expect(group.controls.email.touched).toBe(true);
    expect(group.controls.password.errors).toBeNull();
  });

  it('ignore un champ inconnu du formulaire et le signale', () => {
    expect(applyServerErrors(form(), [{ field: 'role', message: 'interdit' }])).toBe(false);
  });
});

describe('fieldErrorMessage', () => {
  it('donne la priorité au message du serveur', () => {
    expect(fieldErrorMessage({ required: true, server: 'est trop courant' })).toBe(
      'est trop courant',
    );
  });

  it('traduit les validateurs Angular', () => {
    expect(fieldErrorMessage({ required: true })).toBe('Ce champ est obligatoire.');
    expect(fieldErrorMessage({ minlength: { requiredLength: 12, actualLength: 3 } })).toBe(
      'Au moins 12 caractères.',
    );
    expect(fieldErrorMessage(null)).toBeNull();
  });
});

describe('matchingFields', () => {
  const form = (password: string, confirmation: string) =>
    new FormGroup(
      {
        newPassword: new FormControl(password),
        confirmation: new FormControl(confirmation, Validators.required),
      },
      { validators: matchingFields('newPassword', 'confirmation') },
    );

  it('signale une confirmation différente sur le champ de confirmation', () => {
    const group = form('une-phrase-longue', 'une-autre-phrase');

    expect(group.valid).toBe(false);
    expect(group.controls.confirmation.hasError('mismatch')).toBe(true);
  });

  it("retire l'erreur quand les deux saisies concordent", () => {
    const group = form('une-phrase-longue', 'autre');
    group.controls.confirmation.setValue('une-phrase-longue');

    expect(group.valid).toBe(true);
    expect(group.controls.confirmation.errors).toBeNull();
  });
});

describe('Validateurs du profil', () => {
  it('traduit les chemins de liste du serveur', () => {
    expect(toFormPath('skills[2].skillId')).toBe('skills.2.skillId');
    expect(toFormPath('email')).toBe('email');
  });

  it('place une erreur serveur sur une ligne de FormArray', () => {
    const form = new FormGroup({
      skills: new FormArray([
        new FormGroup({ skillId: new FormControl('a') }),
        new FormGroup({ skillId: new FormControl('a') }),
      ]),
    });

    expect(
      applyServerErrors(form, [{ field: 'skills[1].skillId', message: 'Compétence en double.' }]),
    ).toBe(true);
    expect(form.get('skills.1.skillId')?.errors).toEqual({ server: 'Compétence en double.' });
  });

  it('refuse une fin antérieure au début, dates ou années', () => {
    const dates = new FormGroup(
      { start: new FormControl('2022-03-01'), end: new FormControl('2021-01-01') },
      { validators: chronologicalOrder('start', 'end') },
    );
    const years = new FormGroup(
      { start: new FormControl(2017), end: new FormControl(2022) },
      { validators: chronologicalOrder('start', 'end') },
    );

    expect(dates.controls.end.hasError('chronology')).toBe(true);
    expect(years.valid).toBe(true);
  });

  it('accepte une date de fin vide (poste en cours)', () => {
    const group = new FormGroup(
      { start: new FormControl('2022-03-01'), end: new FormControl('') },
      { validators: chronologicalOrder('start', 'end') },
    );
    expect(group.valid).toBe(true);
  });

  it('signale les doublons d’une liste à partir de la deuxième occurrence', () => {
    const array = new FormArray(
      [
        new FormGroup({ languageCode: new FormControl('fr') }),
        new FormGroup({ languageCode: new FormControl('ar') }),
        new FormGroup({ languageCode: new FormControl('fr') }),
      ],
      uniqueBy('languageCode'),
    );

    expect(array.at(0).get('languageCode')?.errors).toBeNull();
    expect(array.at(2).get('languageCode')?.hasError('duplicate')).toBe(true);
  });

  it.each([
    ['https://github.com/salma', null],
    ['http://github.com/salma', { httpsUrl: true }],
    ['javascript:alert(1)', { httpsUrl: true }],
    ['https://localhost', { httpsUrl: true }],
    ['', null],
  ])('httpsUrl(%s)', (value, expected) => {
    expect(httpsUrl(new FormControl(value))).toEqual(expected);
  });

  it('vérifie le format international du téléphone', () => {
    expect(e164Phone(new FormControl('+212600000000'))).toBeNull();
    expect(e164Phone(new FormControl('0600000000'))).toEqual({ phone: true });
    expect(e164Phone(new FormControl(''))).toBeNull();
  });

  it('refuse une date de début dans le futur', () => {
    expect(notInFuture(new FormControl('2999-01-01'))).toEqual({ future: true });
    expect(notInFuture(new FormControl('2020-01-01'))).toBeNull();
  });
});

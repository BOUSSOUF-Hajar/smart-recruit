import { isoDate } from './expiry-dialog';

describe('isoDate', () => {
  it('ajoute des jours calendaires à la date locale', () => {
    expect(isoDate(0, new Date(2026, 9, 22, 23, 30))).toBe('2026-10-22');
    expect(isoDate(30, new Date(2026, 9, 22))).toBe('2026-11-21');
  });

  it('passe correctement les fins de mois et d’année', () => {
    expect(isoDate(1, new Date(2026, 11, 31))).toBe('2027-01-01');
    expect(isoDate(180, new Date(2026, 0, 1))).toBe('2026-06-30');
  });
});

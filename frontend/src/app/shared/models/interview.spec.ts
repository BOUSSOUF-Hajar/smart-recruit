import { isoToLocalInput, localInputToIso } from './interview';

describe('conversion heure locale ↔ UTC (RG-32)', () => {
  it('convertit la saisie locale en UTC selon le fuseau du navigateur', () => {
    const iso = localInputToIso('2026-11-02T10:00')!;
    const offsetMinutes = new Date(2026, 10, 2, 10, 0).getTimezoneOffset();

    expect(iso.endsWith('Z')).toBe(true);
    expect(new Date(iso).getTime()).toBe(Date.UTC(2026, 10, 2, 10, 0) + offsetMinutes * 60_000);
  });

  it("réaffiche l'heure saisie : l'aller-retour ne décale rien", () => {
    expect(isoToLocalInput(localInputToIso('2026-11-02T10:00')!)).toBe('2026-11-02T10:00');
    expect(isoToLocalInput(localInputToIso('2026-12-31T23:45')!)).toBe('2026-12-31T23:45');
  });

  it('refuse une saisie vide ou invalide', () => {
    expect(localInputToIso('')).toBeNull();
    expect(localInputToIso('pas une date')).toBeNull();
  });
});
